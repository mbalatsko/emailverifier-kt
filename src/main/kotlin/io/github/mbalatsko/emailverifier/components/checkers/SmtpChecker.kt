package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.core.SmtpConnectionFactory
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
        if (context.isEmpty()) return SmtpData(false, null, 0, "")

        val fakeLocalPart = "${Random.nextInt(10000, 99999)}catchalltest${Random.nextInt(10000, 99999)}"
        val fakeEmail = "$fakeLocalPart@${email.hostname}"
        var lastIoException: IOException? = null

        for (mx in context) {
            repeat(maxRetries) {
                try {
                    val conn = smtpConnectionFactory.connect(mx.exchange, SMTP_PORT)
                    conn.sendCommand("HELO $HELO_DOMAIN")
                    conn.sendCommand("MAIL FROM:<$HELO_EMAIL>")
                    val resp = conn.sendCommand("RCPT TO:<${email.toStringNoPlus()}>")

                    val deliverable = resp.code in 200..299

                    // Optional catch-all test
                    val catchAll =
                        if (enableAllCatchCheck) {
                            val fakeResp = conn.sendCommand("RCPT TO:<$fakeEmail>")
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

                    return SmtpData(deliverable, catchAll, resp.code, resp.msg)
                } catch (e: IOException) {
                    lastIoException = e
                }
            }
        }

        throw ConnectionError("Failed to connect to any SMTP server", lastIoException)
    }

    companion object {
        private const val HELO_EMAIL = "check@example.com"
        private const val HELO_DOMAIN = "example.com"
        private const val SMTP_PORT = 25
    }
}
