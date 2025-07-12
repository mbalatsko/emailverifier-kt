package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailParts
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecord
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * A sealed class representing the result of a single validation check.
 * It can be in one of four states: Passed, Failed, Skipped, or Errored.
 *
 * @param T the type of data carried by the result.
 */
sealed class CheckResult<out T> {
    /**
     * Indicates that the check was successful.
     * @property data data associated with the passed check.
     */
    data class Passed<T>(
        val data: T,
    ) : CheckResult<T>()

    /**
     * Indicates that the check failed.
     * @property data optional data associated with the failed check.
     */
    data class Failed<T>(
        val data: T? = null,
    ) : CheckResult<T>()

    /**
     * Indicates that the check was skipped.
     */
    data object Skipped : CheckResult<Nothing>()

    /**
     * Indicates that the check produced an error.
     * @property error the throwable that was caught during the check.
     */
    data class Errored(
        val error: Throwable,
    ) : CheckResult<Nothing>()
}

/**
 * Data class holding the validity of each part of the email syntax.
 * @property username true if the username part is valid.
 * @property plusTag true if the plus-tag part is valid.
 * @property hostname true if the hostname part is valid.
 */
data class SyntaxValidationData(
    val username: Boolean,
    val plusTag: Boolean,
    val hostname: Boolean,
)

/**
 * Data class holding the registrable domain found during the registrability check.
 * @property registrableDomain The registrable domain string, or null if not found.
 */
data class RegistrabilityData(
    val registrableDomain: String?,
)

/**
 * Data class holding the MX records found during the MX record check.
 * @property records A list of [MxRecord]s, or an empty list if none were found.
 */
data class MxRecordData(
    val records: List<MxRecord>,
)

/**
 * Data class holding the Gravatar URL found during the Gravatar check.
 * @property gravatarUrl The Gravatar URL string, or null if no custom avatar was found.
 */
data class GravatarData(
    val gravatarUrl: String?,
)

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
 * @property syntax result of syntax validation.
 * @property registrability result of public suffix (PSL) registrability validation.
 * @property mx result of MX record existence check.
 * @property disposable result of disposable domain detection.
 * @property gravatar result of gravatar presence check.
 * @property free result of check against list of known free‐email providers.
 * @property roleBasedUsername result of check against list of role-based usernames.
 * @property smtp result of SMTP check.
 */
data class EmailValidationResult(
    val email: String,
    val emailParts: EmailParts,
    val syntax: CheckResult<SyntaxValidationData>,
    val registrability: CheckResult<RegistrabilityData> = CheckResult.Skipped,
    val mx: CheckResult<MxRecordData> = CheckResult.Skipped,
    val disposable: CheckResult<Unit> = CheckResult.Skipped,
    val gravatar: CheckResult<GravatarData> = CheckResult.Skipped,
    val free: CheckResult<Unit> = CheckResult.Skipped,
    val roleBasedUsername: CheckResult<Unit> = CheckResult.Skipped,
    val smtp: CheckResult<SmtpData> = CheckResult.Skipped,
) {
    /**
     * Returns true if all strong indicator checks passed.
     * Strong indicator checks are: syntax, registrability, mx record presence, and disposability.
     * These checks are the most likely to indicate that an email address is not valid.
     */
    fun isLikelyDeliverable(): Boolean =
        syntax !is CheckResult.Failed &&
            registrability !is CheckResult.Failed &&
            mx !is CheckResult.Failed &&
            disposable !is CheckResult.Failed
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
    private val smtpChecker: SmtpChecker?,
) {
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
                    val syntaxData = SyntaxValidationData(username = false, plusTag = false, hostname = false)
                    return@coroutineScope EmailValidationResult(
                        email = email,
                        emailParts = EmailParts("", "", ""),
                        syntax = CheckResult.Failed(syntaxData),
                    )
                }

            val isUsernameValid = emailSyntaxChecker.isUsernameValid(emailParts.username)
            val isPlusTagValid = emailSyntaxChecker.isPlusTagValid(emailParts.plusTag)
            val isHostnameValid = emailSyntaxChecker.isHostnameValid(emailParts.hostname)
            val syntaxData = SyntaxValidationData(isUsernameValid, isPlusTagValid, isHostnameValid)
            val syntaxCheck =
                if (syntaxData.run { username && plusTag && hostname }) {
                    CheckResult.Passed(syntaxData)
                } else {
                    CheckResult.Failed(syntaxData)
                }

            val registrabilityCheck =
                async {
                    try {
                        if (pslIndex == null || !isHostnameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = pslIndex.findRegistrableDomain(emailParts.hostname)
                            if (result !=
                                null
                            ) {
                                CheckResult.Passed(RegistrabilityData(result))
                            } else {
                                CheckResult.Failed(RegistrabilityData(result))
                            }
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }
            val disposabilityCheck =
                async {
                    try {
                        if (disposableEmailChecker == null || !isHostnameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = disposableEmailChecker.isDisposable(emailParts.hostname)
                            if (!result) CheckResult.Passed(Unit) else CheckResult.Failed(Unit)
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }
            val freeCheck =
                async {
                    try {
                        if (freeChecker == null || !isHostnameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = freeChecker.isFree(emailParts.hostname)
                            if (!result) CheckResult.Passed(Unit) else CheckResult.Failed(Unit)
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }
            val roleBasedUsernameCheck =
                async {
                    try {
                        if (roleBasedUsernameChecker == null || !isUsernameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = roleBasedUsernameChecker.isRoleBased(emailParts.username)
                            if (!result) CheckResult.Passed(Unit) else CheckResult.Failed(Unit)
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }
            val mxRecordCheck =
                async {
                    try {
                        if (mxRecordChecker == null || !isHostnameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = mxRecordChecker.getRecords(emailParts.hostname)
                            if (result.isNotEmpty()) CheckResult.Passed(MxRecordData(result)) else CheckResult.Failed(MxRecordData(result))
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }
            val gravatarCheck =
                async {
                    try {
                        if (gravatarChecker == null || !isUsernameValid || !isHostnameValid) {
                            CheckResult.Skipped
                        } else {
                            val result = gravatarChecker.getGravatarUrl("${emailParts.username}@${emailParts.hostname}")
                            if (result != null) CheckResult.Passed(GravatarData(result)) else CheckResult.Failed(GravatarData(result))
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }

            val mxResult = mxRecordCheck.await()
            val smtpCheck =
                async {
                    try {
                        if (smtpChecker == null || !isUsernameValid || !isHostnameValid || mxResult !is CheckResult.Passed) {
                            CheckResult.Skipped
                        } else {
                            val result =
                                smtpChecker.verifyEmail(
                                    emailParts,
                                    mxResult.data.records,
                                )
                            if (result.isDeliverable) CheckResult.Passed(result) else CheckResult.Failed(result)
                        }
                    } catch (e: Exception) {
                        CheckResult.Errored(e)
                    }
                }

            EmailValidationResult(
                email = email,
                emailParts = emailParts,
                syntax = syntaxCheck,
                registrability = registrabilityCheck.await(),
                mx = mxRecordCheck.await(),
                disposable = disposabilityCheck.await(),
                gravatar = gravatarCheck.await(),
                free = freeCheck.await(),
                roleBasedUsername = roleBasedUsernameCheck.await(),
                smtp = smtpCheck.await(),
            )
        }
}
