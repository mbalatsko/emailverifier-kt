package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.core.SmtpConnectionFactory
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.random.Random

/**
 * Data class holding the results of an SMTP check.
 *
 * @property isDeliverable true if the email address is deliverable.

 * @property isCatchAll true if the server has a catch-all policy, false if not, null if inconclusive.
 * @property smtpCode the last SMTP response code.
 * @property smtpMessage the last SMTP response message.
 */
data class SmtpData(
    val isDeliverable: Boolean,
    val isCatchAll: Boolean?,
    val smtpCode: Int,
    val smtpMessage: String,
)

/**
 * Performs an SMTP check to verify if an email address is deliverable.
 *
 * @property enableAllCatchCheck whether to check if the server has a catch-all policy.
 * @property maxRetries maximum number of retries for SMTP commands.
 * @property smtpConnectionFactory a factory for creating SMTP connections.
 */
class SmtpChecker(
    private val enableAllCatchCheck: Boolean,
    private val maxRetries: Int,
    private val smtpConnectionFactory: SmtpConnectionFactory,
) : IChecker<SmtpData, List<MxRecord>> {
    /**
     * Verifies an email address by connecting to the mail server and simulating sending an email.
     *
     * @param email the decomposed parts of the email address to verify.
     * @param context a list of MX records for the domain.
     * @return an [SmtpData] object with the verification results.
     */
    override suspend fun check(
        email: EmailParts,
        context: List<MxRecord>,
    ): SmtpData {
        if (context.isEmpty()) {
            logger.warn("SMTP check for {} skipped: no MX records found.", email)
            return SmtpData(false, null, 0, "")
        }

        val fakeLocalPart = "${Random.nextInt(10000, 99999)}catchalltest${Random.nextInt(10000, 99999)}"
        val fakeEmail = "$fakeLocalPart@${email.hostname}"
        var lastIoException: IOException? = null

        logger.debug("Starting SMTP check for {} with {} MX records.", email, context.size)
        for (mx in context) {
            repeat(maxRetries) { attempt ->
                try {
                    logger.trace("Connecting to {} (attempt {}/{})", mx.exchange, attempt + 1, maxRetries)
                    val conn = smtpConnectionFactory.connect(mx.exchange, SMTP_PORT)
                    conn.sendCommand("HELO $HELO_DOMAIN")
                    conn.sendCommand("MAIL FROM:<$HELO_EMAIL>")
                    val resp = conn.sendCommand("RCPT TO:<${email.toStringNoPlus()}>")
                    logger.trace("RCPT TO response: {}", resp)

                    val deliverable = resp.code in 200..299

                    // Optional catch-all test
                    val catchAll =
                        if (enableAllCatchCheck) {
                            logger.trace("Performing catch-all check with fake email: {}", fakeEmail)
                            val fakeResp = conn.sendCommand("RCPT TO:<$fakeEmail>")
                            logger.trace("Catch-all response: {}", fakeResp)
                            when (fakeResp.code) {
                                in 200..299 -> true
                                in 500..599 -> false
                                else -> null // inconclusive
                            }
                        } else {
                            null
                        }

                    conn.sendCommand("QUIT")
                    conn.close()

                    logger.debug(
                        "SMTP check for {} completed. Deliverable: {}, Catch-all: {}",
                        email,
                        deliverable,
                        catchAll,
                    )
                    return SmtpData(deliverable, catchAll, resp.code, resp.msg)
                } catch (e: IOException) {
                    lastIoException = e
                    logger.warn("Failed to connect to {} on attempt {}: {}", mx.exchange, attempt + 1, e.message)
                }
            }
        }

        logger.error("Failed to connect to any SMTP server for {}.", email, lastIoException)
        throw ConnectionError("Failed to connect to any SMTP server", lastIoException)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SmtpChecker::class.java)
        private const val HELO_EMAIL = "check@example.com"
        private const val HELO_DOMAIN = "example.com"
        private const val SMTP_PORT = 25
    }
}
