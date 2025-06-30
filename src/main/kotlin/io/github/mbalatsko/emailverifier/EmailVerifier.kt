package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider

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
 */
data class EmailValidationResult(
    val email: String,
    val syntaxCheck: CheckResult,
    val registrabilityCheck: CheckResult = CheckResult.SKIPPED,
    val mxRecordCheck: CheckResult = CheckResult.SKIPPED,
    val disposabilityCheck: CheckResult = CheckResult.SKIPPED,
) {
    /**
     * Returns true if all checks either passed or were skipped.
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
 * MX record verification, and detection of disposable email domains.
 *
 * Each validation stage can be enabled/disabled via [EmailVerifierConfig].
 */
class EmailVerifier(
    private val emailSyntaxChecker: EmailSyntaxChecker,
    private val pslIndex: PslIndex?,
    private val mxRecordChecker: MxRecordChecker?,
    private val disposableEmailChecker: DisposableEmailChecker?,
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

        return EmailValidationResult(
            email,
            syntaxCheck,
            registrabilityCheck,
            mxRecordCheck,
            disposabilityCheck,
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
            val emailSyntaxChecker = EmailSyntaxChecker()

            val pslIndex =
                if (config.enableRegistrabilityCheck) {
                    PslIndex.init(OnlineLFDomainsProvider(config.pslURL))
                } else {
                    null
                }

            val mxRecordChecker =
                if (config.enableMxRecordCheck) {
                    MxRecordChecker(GoogleDoHLookupBackend(config.dohServerEndpoint))
                } else {
                    null
                }

            val disposableEmailChecker =
                if (config.enableDisposabilityCheck) {
                    DisposableEmailChecker.init(
                        OnlineLFDomainsProvider(config.disposableDomainsListUrl),
                    )
                } else {
                    null
                }

            return EmailVerifier(emailSyntaxChecker, pslIndex, mxRecordChecker, disposableEmailChecker)
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
 */
data class EmailVerifierConfig(
    val enableRegistrabilityCheck: Boolean = true,
    val pslURL: String = PslIndex.MOZILLA_PSL_URL,
    val enableMxRecordCheck: Boolean = true,
    val dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL,
    val enableDisposabilityCheck: Boolean = true,
    val disposableDomainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL,
)
