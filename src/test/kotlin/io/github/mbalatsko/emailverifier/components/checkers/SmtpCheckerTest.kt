package io.github.mbalatsko.emailverifier.components.checkers

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtpCheckerTest {
    @Test
    fun `verifyEmail returns deliverable when RCPT TO is successful`() =
        runTest {
            // Arrange
            val mockConnection = MockSmtpConnection()
            val mockConnectionFactory = SmtpConnectionFactory { _, _ -> mockConnection }

            // Simulate the SMTP conversation
            mockConnection.addResponse(250, "OK") // HELO
            mockConnection.addResponse(250, "OK") // MAIL FROM
            mockConnection.addResponse(250, "OK, user exists") // RCPT TO
            mockConnection.addResponse(221, "Bye") // QUIT

            val checker =
                SmtpChecker(
                    enableAllCatchCheck = false,
                    maxRetries = 1,
                    smtpConnectionFactory = mockConnectionFactory,
                )
            val emailParts = EmailParts("user", "", "domain.com")
            val mxRecords = listOf(MxRecord("mx.domain.com", 10))

            // Act
            val result = checker.verifyEmail(emailParts, mxRecords)

            // Assert
            assertTrue(result.isDeliverable)
            assertEquals(250, result.smtpCode)
            assertEquals("OK, user exists", result.smtpMessage)
        }

    @Test
    fun `verifyEmail returns not deliverable when RCPT TO fails`() =
        runTest {
            // Arrange
            val mockConnection = MockSmtpConnection()
            val mockConnectionFactory = SmtpConnectionFactory { _, _ -> mockConnection }

            mockConnection.addResponse(250, "OK") // HELO
            mockConnection.addResponse(250, "OK") // MAIL FROM
            mockConnection.addResponse(550, "No such user") // RCPT TO
            mockConnection.addResponse(221, "Bye") // QUIT

            val checker =
                SmtpChecker(
                    enableAllCatchCheck = false,
                    maxRetries = 1,
                    smtpConnectionFactory = mockConnectionFactory,
                )
            val emailParts = EmailParts("user", "", "domain.com")
            val mxRecords = listOf(MxRecord("mx.domain.com", 10))

            // Act
            val result = checker.verifyEmail(emailParts, mxRecords)

            // Assert
            assertFalse(result.isDeliverable)
            assertEquals(550, result.smtpCode)
            assertEquals("No such user", result.smtpMessage)
        }

    @Test
    fun `detects catch-all when enabled and fake email is accepted`() =
        runTest {
            // Arrange
            val mockConnection = MockSmtpConnection()
            val mockConnectionFactory = SmtpConnectionFactory { _, _ -> mockConnection }

            mockConnection.addResponse(250, "OK") // HELO
            mockConnection.addResponse(250, "OK") // MAIL FROM
            mockConnection.addResponse(250, "OK, user exists") // RCPT TO (real email)
            mockConnection.addResponse(250, "OK, accepted") // RCPT TO (fake email)
            mockConnection.addResponse(221, "Bye") // QUIT

            val checker =
                SmtpChecker(
                    enableAllCatchCheck = true,
                    maxRetries = 1,
                    smtpConnectionFactory = mockConnectionFactory,
                )
            val emailParts = EmailParts("user", "", "domain.com")
            val mxRecords = listOf(MxRecord("mx.domain.com", 10))

            // Act
            val result = checker.verifyEmail(emailParts, mxRecords)

            // Assert
            assertTrue(result.isDeliverable)
            assertTrue(result.isCatchAll == true)
        }

    @Test
    fun `handles connection IOException and retries with next MX record`() =
        runTest {
            var i = 0
            val mockConnectionFactory =
                SmtpConnectionFactory { _, _ ->
                    when (i) {
                        0 -> {
                            i++
                            MockSmtpConnection(throwOnConnect = true)
                        }
                        else ->
                            MockSmtpConnection().apply {
                                addResponse(250, "OK") // HELO
                                addResponse(250, "OK") // MAIL FROM
                                addResponse(250, "OK") // RCPT TO
                                addResponse(221, "Bye") // QUIT
                            }
                    }
                }

            val checker =
                SmtpChecker(
                    enableAllCatchCheck = false,
                    maxRetries = 1,
                    smtpConnectionFactory = mockConnectionFactory,
                )
            val emailParts = EmailParts("user", "", "domain.com")
            val mxRecords =
                listOf(
                    MxRecord("mx1.domain.com", 10),
                    MxRecord("mx2.domain.com", 20),
                )

            val result = checker.verifyEmail(emailParts, mxRecords)

            assertTrue(result.isDeliverable)
            assertEquals(250, result.smtpCode)
        }
}

/**
 * A simple mock implementation of ISmptConnection for testing.
 */
class MockSmtpConnection(
    throwOnConnect: Boolean = false,
) : ISmtpConnection {
    private val responses = mutableListOf<SmtpResponse>()
    private var connectCalled = false
    private var closeCalled = false

    init {
        if (throwOnConnect) {
            throw java.io.IOException("Connection failed")
        }
        connectCalled = true
    }

    fun addResponse(
        code: Int,
        msg: String,
    ) {
        responses.add(SmtpResponse(code, msg))
    }

    override fun sendCommand(cmd: String): SmtpResponse {
        assertTrue(connectCalled, "connect() must be called before sendCommand()")
        assertFalse(closeCalled, "sendCommand() should not be called after close()")
        return responses.removeAt(0)
    }

    override fun close() {
        closeCalled = true
    }
}
