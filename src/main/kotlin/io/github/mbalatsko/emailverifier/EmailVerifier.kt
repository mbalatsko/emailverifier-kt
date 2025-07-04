package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class CheckResult {
    PASSED,
    FAILED,
    SKIPPED,
    ERRORED,
}

/**
 * Custom exception to signal that a verification check failed due to an external error.
 */
class VerificationError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Aggregated result of a full email validation process.
 *
 * @property email the input email address.
 * @property syntaxCheck result of syntax validation.
 * @property registrabilityCheck result of public suffix (PSL) registrability validation.
 * @property mxRecordCheck result of MX record existence check.
 * @property disposabilityCheck result of disposable domain detection.
 * @property gravatarCheck result of gravatar presence check, `FAILED` if gravatar is not present
 * @property freeCheck result of check against list of known free‐email provider, `PASSED` if email hostname is not a known free‐email provider
 * @property roleBasedUsernameCheck result of check against list of role-based usernames, `PASSED` if email username is not a role-based username
 */
data class EmailValidationResult(
    val email: String,
    val syntaxCheck: CheckResult,
    val registrabilityCheck: CheckResult = CheckResult.SKIPPED,
    val mxRecordCheck: CheckResult = CheckResult.SKIPPED,
    val disposabilityCheck: CheckResult = CheckResult.SKIPPED,
    val gravatarCheck: CheckResult = CheckResult.SKIPPED,
    val freeCheck: CheckResult = CheckResult.SKIPPED,
    val roleBasedUsernameCheck: CheckResult = CheckResult.SKIPPED,
) {
    /**
     * Returns true if all strong indicator checks either passed or were skipped.
     * Strong indicator checks: syntax, registrability, mx record presence, disposability
     */
    fun ok(): Boolean =
        syntaxCheck != CheckResult.FAILED &&
            registrabilityCheck != CheckResult.FAILED &&
            mxRecordCheck != CheckResult.FAILED &&
            disposabilityCheck != CheckResult.FAILED &&
            // ERRORED is not a failure condition for ok()
            syntaxCheck != CheckResult.ERRORED &&
            registrabilityCheck != CheckResult.ERRORED &&
            mxRecordCheck != CheckResult.ERRORED &&
            disposabilityCheck != CheckResult.ERRORED
}

/**
 * Main entry point for structured email validation.
 *
 * Performs syntax validation, registrability checks using the Public Suffix List,
 * MX record verification, detection of disposable email domains, gravatar existence check,
 * check against list of known free‐email providers, check against list of role-based usernames.
 *
 * Each validation stage can be enabled/disabled via [EmailVerifierConfig].
 */
class EmailVerifier(
    private val emailSyntaxChecker: EmailSyntaxChecker,
    private val pslIndex: PslIndex?,
    private val mxRecordChecker: MxRecordChecker?,
    private val disposableEmailChecker: DisposableEmailChecker?,
    private val gravatarChecker: GravatarChecker?,
    private val freeChecker: FreeChecker?,
    private val roleBasedUsernameChecker: RoleBasedUsernameChecker?,
) {
    /**
     * Validates the given email address using configured checks.
     *
     * @param email the input email address to validate.
     * @return a structured [EmailValidationResult] with results for each check.
     */
    private suspend fun runCheck(
        checker: Any?,
        precondition: Boolean = true,
        action: suspend () -> Boolean,
    ): CheckResult =
        try {
            when {
                checker == null -> CheckResult.SKIPPED
                !precondition -> CheckResult.SKIPPED
                action() -> CheckResult.PASSED
                else -> CheckResult.FAILED
            }
        } catch (e: VerificationError) {
            CheckResult.ERRORED
        }

    /**
     * Validates the given email address using configured checks.
     *
     * @param email the input email address to validate.
     * @return a structured [EmailValidationResult] with results for each check.
     */
    suspend fun verify(email: String): EmailValidationResult =
        coroutineScope {
            val emailParts =
                try {
                    emailSyntaxChecker.parseEmailParts(email)
                } catch (_: IllegalArgumentException) {
                    return@coroutineScope EmailValidationResult(
                        email,
                        CheckResult.FAILED,
                    )
                }

            val isUsernameValid = emailSyntaxChecker.isUsernameValid(emailParts.username)
            val isPlusTagValid = emailSyntaxChecker.isPlusTagValid(emailParts.plusTag)
            val isHostnameValid = emailSyntaxChecker.isHostnameValid(emailParts.hostname)
            val syntaxCheck =
                if (isUsernameValid && isPlusTagValid && isHostnameValid) CheckResult.PASSED else CheckResult.FAILED

            if (syntaxCheck == CheckResult.FAILED) {
                return@coroutineScope EmailValidationResult(
                    email,
                    syntaxCheck,
                )
            }

            val registrabilityCheck =
                runCheck(pslIndex) {
                    pslIndex!!.isHostnameRegistrable(emailParts.hostname)
                }
            val disposabilityCheck =
                runCheck(disposableEmailChecker) {
                    !disposableEmailChecker!!.isDisposable(emailParts.hostname)
                }
            val freeCheck =
                runCheck(freeChecker) {
                    !freeChecker!!.isFree(emailParts.hostname)
                }
            val roleBasedUsernameCheck =
                runCheck(roleBasedUsernameChecker, isUsernameValid) {
                    !roleBasedUsernameChecker!!.isRoleBased(emailParts.username)
                }

            val mxRecordCheck =
                async {
                    runCheck(mxRecordChecker) {
                        mxRecordChecker!!.isPresent(emailParts.hostname)
                    }
                }
            val gravatarCheck =
                async {
                    runCheck(gravatarChecker, isUsernameValid) {
                        gravatarChecker!!.hasGravatar("${emailParts.username}@${emailParts.hostname}")
                    }
                }

            EmailValidationResult(
                email,
                syntaxCheck,
                registrabilityCheck,
                mxRecordCheck.await(),
                disposabilityCheck,
                gravatarCheck.await(),
                freeCheck,
                roleBasedUsernameCheck,
            )
        }

    companion object {
        /**
         * Initializes a new [EmailVerifier] with the specified configuration.
         *
         * Downloads external data as required (PSL, disposable lists) via HTTP.
         *
         * @param config the configuration specifying which checks to enable and which URLs to use.
         * @return an initialized [EmailVerifier] ready for use.
         */
        suspend fun init(config: EmailVerifierConfig = EmailVerifierConfig()): EmailVerifier =
            coroutineScope {
                val httpClient = config.httpClient ?: HttpClient(CIO)
                val emailSyntaxChecker = EmailSyntaxChecker()

                val pslIndex =
                    if (config.enableRegistrabilityCheck) {
                        async { PslIndex.init(OnlineLFDomainsProvider(config.pslURL, httpClient)) }
                    } else {
                        null
                    }

                val disposableEmailChecker =
                    if (config.enableDisposabilityCheck) {
                        async {
                            DisposableEmailChecker.init(
                                OnlineLFDomainsProvider(config.disposableDomainsListUrl, httpClient),
                            )
                        }
                    } else {
                        null
                    }

                val freeChecker =
                    if (config.enableFreeCheck) {
                        async { FreeChecker.init(OnlineLFDomainsProvider(config.freeEmailsListUrl, httpClient)) }
                    } else {
                        null
                    }

                val roleBasedUsernameChecker =
                    if (config.enableRoleBasedUsernameCheck) {
                        async {
                            RoleBasedUsernameChecker.init(OnlineLFDomainsProvider(config.roleBasedUsernamesListUrl, httpClient))
                        }
                    } else {
                        null
                    }

                val mxRecordChecker =
                    if (config.enableMxRecordCheck) {
                        MxRecordChecker(GoogleDoHLookupBackend(httpClient, config.dohServerEndpoint))
                    } else {
                        null
                    }

                val gravatarChecker =
                    if (config.enableGravatarCheck) {
                        GravatarChecker(httpClient)
                    } else {
                        null
                    }

                EmailVerifier(
                    emailSyntaxChecker,
                    pslIndex?.await(),
                    mxRecordChecker,
                    disposableEmailChecker?.await(),
                    gravatarChecker,
                    freeChecker?.await(),
                    roleBasedUsernameChecker?.await(),
                )
            }
    }
}

/**
 * Configuration options for [EmailVerifier] initialization.
 *
 * Each check can be toggled independently. URLs for required external lists
 * (e.g. PSL, disposable domains) and DoH endpoint can also be customized.
 *
 * @property enableRegistrabilityCheck enables use of the Public Suffix List.
 * @property pslURL the source URL for the PSL (Public suffix list).
 * @property enableMxRecordCheck enables DNS MX record lookups.
 * @property dohServerEndpoint the DoH resolver endpoint (used by MX check). See [GoogleDoHLookupBackend] for expected URL format and server behaviour.
 * @property enableDisposabilityCheck enables detection of disposable domains.
 * @property disposableDomainsListUrl the URL of the domain list for disposability check.
 * @property enableGravatarCheck enables Gravatar presence check
 * @property enableFreeCheck enables check against list of known free‐email providers.
 * @property freeEmailsListUrl the URL of the domain list for free-email provider check.
 * @property enableRoleBasedUsernameCheck enables check against list of role-based usernames.
 * @property roleBasedUsernamesListUrl the URL of role-based usernames list.
 * @property httpClient optional custom HTTP client.
 */
data class EmailVerifierConfig(
    val enableRegistrabilityCheck: Boolean = true,
    val pslURL: String = PslIndex.MOZILLA_PSL_URL,
    val enableMxRecordCheck: Boolean = true,
    val dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL,
    val enableDisposabilityCheck: Boolean = true,
    val disposableDomainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL,
    val enableGravatarCheck: Boolean = true,
    val enableFreeCheck: Boolean = true,
    val freeEmailsListUrl: String = FreeChecker.FREE_EMAILS_LIST_URL,
    val enableRoleBasedUsernameCheck: Boolean = true,
    val roleBasedUsernamesListUrl: String = RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_URL,
    val httpClient: HttpClient? = null,
)
