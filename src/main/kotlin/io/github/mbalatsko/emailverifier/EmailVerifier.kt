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

enum class CheckResult {
    PASSED,
    FAILED,
    SKIPPED,
}

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
            disposabilityCheck != CheckResult.FAILED
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
    suspend fun verify(email: String): EmailValidationResult {
        val emailParts =
            try {
                emailSyntaxChecker.parseEmailParts(email)
            } catch (_: IllegalArgumentException) {
                return EmailValidationResult(
                    email,
                    CheckResult.FAILED,
                )
            }

        val isUsernameValid = emailSyntaxChecker.isUsernameValid(emailParts.username)
        val isPlusTagValid = emailSyntaxChecker.isPlusTagValid(emailParts.plusTag)
        val isHostnameValid = emailSyntaxChecker.isHostnameValid(emailParts.hostname)
        val syntaxCheck =
            if (isUsernameValid && isPlusTagValid && isHostnameValid) CheckResult.PASSED else CheckResult.FAILED

        val registrabilityCheck =
            if (pslIndex != null &&
                isHostnameValid &&
                pslIndex.isHostnameRegistrable(emailParts.hostname)
            ) {
                CheckResult.PASSED
            } else if (pslIndex == null || !isHostnameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        val mxRecordCheck =
            if (mxRecordChecker != null &&
                isHostnameValid &&
                mxRecordChecker.isPresent(emailParts.hostname)
            ) {
                CheckResult.PASSED
            } else if (mxRecordChecker == null || !isHostnameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        val disposabilityCheck =
            if (disposableEmailChecker != null &&
                isHostnameValid &&
                !disposableEmailChecker.isDisposable(emailParts.hostname)
            ) {
                CheckResult.PASSED
            } else if (disposableEmailChecker == null || !isHostnameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        val gravatarCheck =
            if (gravatarChecker != null &&
                isHostnameValid &&
                isUsernameValid &&
                gravatarChecker.hasGravatar("${emailParts.username}@${emailParts.hostname}")
            ) {
                CheckResult.PASSED
            } else if (gravatarChecker == null || !isHostnameValid || !isUsernameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        val freeCheck =
            if (freeChecker != null &&
                isHostnameValid &&
                !freeChecker.isFree(emailParts.hostname)
            ) {
                CheckResult.PASSED
            } else if (freeChecker == null || !isHostnameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        val roleBasedUsernameCheck =
            if (roleBasedUsernameChecker != null &&
                isUsernameValid &&
                !roleBasedUsernameChecker.isRoleBased(emailParts.username)
            ) {
                CheckResult.PASSED
            } else if (roleBasedUsernameChecker == null || !isUsernameValid) {
                CheckResult.SKIPPED
            } else {
                CheckResult.FAILED
            }

        return EmailValidationResult(
            email,
            syntaxCheck,
            registrabilityCheck,
            mxRecordCheck,
            disposabilityCheck,
            gravatarCheck,
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
        suspend fun init(config: EmailVerifierConfig = EmailVerifierConfig()): EmailVerifier {
            val httpClient = HttpClient(CIO)
            val emailSyntaxChecker = EmailSyntaxChecker()

            val pslIndex =
                if (config.enableRegistrabilityCheck) {
                    PslIndex.init(OnlineLFDomainsProvider(config.pslURL, httpClient))
                } else {
                    null
                }

            val mxRecordChecker =
                if (config.enableMxRecordCheck) {
                    MxRecordChecker(GoogleDoHLookupBackend(httpClient, config.dohServerEndpoint))
                } else {
                    null
                }

            val disposableEmailChecker =
                if (config.enableDisposabilityCheck) {
                    DisposableEmailChecker.init(
                        OnlineLFDomainsProvider(config.disposableDomainsListUrl, httpClient),
                    )
                } else {
                    null
                }

            val gravatarChecker =
                if (config.enableGravatarCheck) {
                    GravatarChecker(httpClient)
                } else {
                    null
                }

            val freeChecker =
                if (config.enableFreeCheck) {
                    FreeChecker.init(OnlineLFDomainsProvider(config.freeEmailsListUrl, httpClient))
                } else {
                    null
                }

            val roleBasedUsernameChecker =
                if (config.enableRoleBasedUsernameCheck) {
                    RoleBasedUsernameChecker.init(OnlineLFDomainsProvider(config.roleBasedUsernamesListUrl, httpClient))
                } else {
                    null
                }

            return EmailVerifier(
                emailSyntaxChecker,
                pslIndex,
                mxRecordChecker,
                disposableEmailChecker,
                gravatarChecker,
                freeChecker,
                roleBasedUsernameChecker,
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
)
