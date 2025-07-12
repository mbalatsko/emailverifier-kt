package io.github.mbalatsko.emailverifier.components.checkers

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlin.random.Random

/**
 * Represents a response from an SMTP server.
 *
 * @property code The 3-digit SMTP response code.
 * @property msg The response message.
 */
data class SmtpResponse(
    val code: Int,
    val msg: String,
)

/**
 * An interface for an SMTP connection, abstracting the underlying socket communication.
 */
interface ISmtpConnection {
    /**
     * Sends a command to the SMTP server.
     *
     * @param cmd The command string to send.
     * @return The server's [SmtpResponse].
     */
    fun sendCommand(cmd: String): SmtpResponse

    /**
     * Closes the connection to the SMTP server.
     */
    fun close()
}

/**
 * A concrete implementation of [ISmtpConnection] using a [java.net.Socket].
 *
 * @property socket The underlying socket for the connection.
 * @property reader The buffered reader for receiving server responses.
 * @property writer The buffered writer for sending commands.
 *
 * @param address The server address to connect to.
 * @param port The server port to connect to.
 * @param timeoutMillis The connection and read timeout in milliseconds.
 * @param proxy The proxy to use for the connection, or null for a direct connection.
 */
class SocketSmtpConnection(
    address: String,
    port: Int,
    timeoutMillis: Int,
    proxy: Proxy?,
) : ISmtpConnection {
    val socket = if (proxy != null) Socket(proxy) else Socket()
    var reader: BufferedReader
    var writer: BufferedWriter

    init {
        socket.connect(InetSocketAddress(address, port), timeoutMillis)
        socket.soTimeout = timeoutMillis

        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        val resp = readResponse()
        require(resp.code == 220) { "Server did not respond with 220 code, ${resp.code} got instead" }
    }

    private fun readResponse(): SmtpResponse {
        val line = reader.readLine() ?: return SmtpResponse(0, "")
        val code = line.take(3).toIntOrNull() ?: 0
        return SmtpResponse(code, line)
    }

    override fun sendCommand(cmd: String): SmtpResponse {
        writer.write("$cmd\r\n")
        writer.flush()
        return readResponse()
    }

    override fun close() {
        socket.close()
    }
}

/**
 * A factory for creating [ISmtpConnection] instances.
 */
fun interface SmtpConnectionFactory {
    /**
     * Creates and connects an [ISmtpConnection].
     *
     * @param address The server address to connect to.
     * @param port The server port to connect to.
     * @return An initialized [ISmtpConnection].
     */
    fun connect(
        address: String,
        port: Int,
    ): ISmtpConnection
}

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
 */
class SmtpChecker(
    private val enableAllCatchCheck: Boolean,
    private val maxRetries: Int,
    private val smtpConnectionFactory: SmtpConnectionFactory,
) {
    /**
     * Verifies an email address by connecting to the mail server and simulating sending an email.
     *
     * @param email the email address to verify.
     * @param mxRecords a list of MX records for the domain.
     * @return an [SmtpData] object with the verification results.
     */
    fun verifyEmail(
        email: EmailParts,
        mxRecords: List<MxRecord>,
    ): SmtpData {
        if (mxRecords.isEmpty()) return SmtpData(false, null, 0, "")

        val fakeLocalPart = "${Random.nextInt(10000, 99999)}catchalltest${Random.nextInt(10000, 99999)}"
        val fakeEmail = "$fakeLocalPart@${email.hostname}"

        for (mx in mxRecords) {
            repeat(maxRetries) { attempt ->
                try {
                    val conn = smtpConnectionFactory.connect(mx.exchange, SMTP_PORT)
                    conn.sendCommand("HELO $HELO_DOMAIN")
                    conn.sendCommand("MAIL FROM:<$HELO_EMAIL>")
                    val resp = conn.sendCommand("RCPT TO:<${email.username}@${email.hostname}>")

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
                } catch (_: IOException) {
                }
            }
        }

        return SmtpData(false, null, 0, "")
    }

    companion object {
        private const val HELO_EMAIL = "check@example.com"
        private const val HELO_DOMAIN = "example.com"
        private const val SMTP_PORT = 25
    }
}
