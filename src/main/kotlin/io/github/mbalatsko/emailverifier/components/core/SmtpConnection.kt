package io.github.mbalatsko.emailverifier.components.core

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

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
