package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GravatarCheckerTest {
    private val testEmail = EmailParts("test", "", "example.com")

    @Test
    fun `check returns URL for existing gravatar`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("somehash", HttpStatusCode.OK)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertNotNull(checker.check(testEmail, Unit))
        }

    @Test
    fun `check returns null for non-existent gravatar`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("", HttpStatusCode.NotFound)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertNull(checker.check(testEmail, Unit).gravatarUrl)
        }

    @Test
    fun `check throws ConnectionError on 4xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.BadRequest)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertFailsWith<ConnectionError> {
                checker.check(testEmail, Unit)
            }
        }

    @Test
    fun `check throws ConnectionError on 5xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.InternalServerError)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertFailsWith<ConnectionError> {
                checker.check(testEmail, Unit)
            }
        }
}
