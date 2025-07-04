package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.ktor.client.HttpClient
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
     * Note: mx record presence might return ERRORED, which is not validated
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
     * Executes a verification check, handling null checkers, preconditions, and errors.
     *
     * @param checker The checker instance; if null, the check is skipped.
     * @param precondition An optional boolean precondition; if false, the check is skipped.
     * @param action The suspendable lambda representing the actual check logic.
     * @return The [CheckResult] based on the action's outcome or skipping conditions.
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

            val registrabilityCheck =
                runCheck(pslIndex, isHostnameValid) {
                    pslIndex!!.isHostnameRegistrable(emailParts.hostname)
                }
            val disposabilityCheck =
                runCheck(disposableEmailChecker, isHostnameValid) {
                    !disposableEmailChecker!!.isDisposable(emailParts.hostname)
                }
            val freeCheck =
                runCheck(freeChecker, isHostnameValid) {
                    !freeChecker!!.isFree(emailParts.hostname)
                }
            val roleBasedUsernameCheck =
                runCheck(roleBasedUsernameChecker, isUsernameValid) {
                    !roleBasedUsernameChecker!!.isRoleBased(emailParts.username)
                }

            val mxRecordCheck =
                async {
                    runCheck(mxRecordChecker, isHostnameValid) {
                        mxRecordChecker!!.isPresent(emailParts.hostname)
                    }
                }
            val gravatarCheck =
                async {
                    runCheck(gravatarChecker, isUsernameValid && isHostnameValid) {
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
}
