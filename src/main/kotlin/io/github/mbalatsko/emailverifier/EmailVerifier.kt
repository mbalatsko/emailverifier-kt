package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DatasetData
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.GravatarData
import io.github.mbalatsko.emailverifier.components.checkers.HostnameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.checkers.IChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordData
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityChecker
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityData
import io.github.mbalatsko.emailverifier.components.checkers.SmtpChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpData
import io.github.mbalatsko.emailverifier.components.checkers.SyntaxValidationData
import io.github.mbalatsko.emailverifier.components.checkers.UsernameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.core.CheckResult
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.core.parseEmailParts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Aggregated result of a full email validation process.
 *
 * @property email the input email address.
 * @property emailParts the decomposed parts of the email address.
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
    val disposable: CheckResult<DatasetData> = CheckResult.Skipped,
    val gravatar: CheckResult<GravatarData> = CheckResult.Skipped,
    val free: CheckResult<DatasetData> = CheckResult.Skipped,
    val roleBasedUsername: CheckResult<DatasetData> = CheckResult.Skipped,
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
    private val registrabilityChecker: RegistrabilityChecker?,
    private val mxRecordChecker: MxRecordChecker?,
    private val disposableEmailChecker: HostnameInDatasetChecker?,
    private val gravatarChecker: GravatarChecker?,
    private val freeChecker: HostnameInDatasetChecker?,
    private val roleBasedUsernameChecker: UsernameInDatasetChecker?,
    private val smtpChecker: SmtpChecker?,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(EmailVerifier::class.java)
    }

    /**
     * Validates the given email address using configured checks.
     *
     * @param email the input email address to validate.
     * @return a structured [EmailValidationResult] with results for each check.
     */
    suspend fun verify(email: String): EmailValidationResult {
        logger.info("Starting verification for email: {}", email)
        val result =
            coroutineScope {
                val emailParts =
                    try {
                        parseEmailParts(email)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Failed to parse email: {}. Reason: {}", email, e.message)
                        val syntaxData = SyntaxValidationData(username = false, plusTag = false, hostname = false)
                        return@coroutineScope EmailValidationResult(
                            email = email,
                            emailParts = EmailParts("", "", ""),
                            syntax = CheckResult.Failed(syntaxData),
                        )
                    }
                logger.debug("Parsed email parts: {}", emailParts)

                val syntaxData = emailSyntaxChecker.check(emailParts, Unit)
                val syntaxCheck =
                    if (syntaxData.run { username && plusTag && hostname }) {
                        CheckResult.Passed(syntaxData)
                    } else {
                        CheckResult.Failed(syntaxData)
                    }
                logger.debug("Syntax check result: {}", syntaxCheck)

                val registrabilityCheck =
                    async {
                        runCheck(registrabilityChecker, emailParts, syntaxData.hostname) {
                            it.registrableDomain != null
                        }
                    }
                val disposabilityCheck =
                    async {
                        runCheck(disposableEmailChecker, emailParts, syntaxData.hostname) {
                            !it.match
                        }
                    }
                val freeCheck =
                    async {
                        runCheck(freeChecker, emailParts, syntaxData.hostname) {
                            !it.match
                        }
                    }
                val roleBasedUsernameCheck =
                    async {
                        runCheck(roleBasedUsernameChecker, emailParts, syntaxData.username) {
                            !it.match
                        }
                    }
                val mxRecordCheck =
                    async {
                        runCheck(mxRecordChecker, emailParts, syntaxData.hostname) {
                            it.records.isNotEmpty()
                        }
                    }
                val gravatarCheck =
                    async {
                        runCheck(gravatarChecker, emailParts, syntaxData.username && syntaxData.hostname) {
                            it.gravatarUrl != null
                        }
                    }

                val mxResult = mxRecordCheck.await()
                logger.debug("MX check result: {}", mxResult)
                val smtpCheck =
                    async {
                        runCheck(
                            smtpChecker,
                            emailParts,
                            (mxResult as? CheckResult.Passed)?.data?.records ?: emptyList(),
                            syntaxData.username && syntaxData.hostname && mxResult is CheckResult.Passed,
                        ) {
                            it.isDeliverable
                        }
                    }

                EmailValidationResult(
                    email = email,
                    emailParts = emailParts,
                    syntax = syntaxCheck,
                    registrability = registrabilityCheck.await().also { logger.debug("Registrability check result: {}", it) },
                    mx = mxResult,
                    disposable = disposabilityCheck.await().also { logger.debug("Disposability check result: {}", it) },
                    gravatar = gravatarCheck.await().also { logger.debug("Gravatar check result: {}", it) },
                    free = freeCheck.await().also { logger.debug("Free check result: {}", it) },
                    roleBasedUsername = roleBasedUsernameCheck.await().also { logger.debug("Role-based username check result: {}", it) },
                    smtp = smtpCheck.await().also { logger.debug("SMTP check result: {}", it) },
                )
            }
        logger.info("Verification finished for email: {}. Result: {}", email, result)
        return result
    }

    private suspend fun <T, C> runCheck(
        checker: IChecker<T, C>?,
        emailParts: EmailParts,
        context: C,
        condition: Boolean = true,
        successCondition: (T) -> Boolean,
    ): CheckResult<T> {
        val checkerName = checker?.let { it::class.java.simpleName } ?: "UnknownChecker"
        if (checker == null || !condition) {
            logger.trace("[{}] Skipped.", checkerName)
            return CheckResult.Skipped
        }
        logger.trace("[{}] Running check for {}...", checkerName, emailParts)
        return try {
            val result = checker.check(emailParts, context)
            if (successCondition(result)) {
                logger.trace("[{}] Passed with data: {}", checkerName, result)
                CheckResult.Passed(result)
            } else {
                logger.trace("[{}] Failed with data: {}", checkerName, result)
                CheckResult.Failed(result)
            }
        } catch (e: Exception) {
            logger.warn("[{}] Errored with exception:", checkerName, e)
            CheckResult.Errored(e)
        }
    }

    private suspend fun <T> runCheck(
        checker: IChecker<T, Unit>?,
        emailParts: EmailParts,
        condition: Boolean = true,
        successCondition: (T) -> Boolean,
    ): CheckResult<T> = runCheck(checker, emailParts, Unit, condition, successCondition)
}
