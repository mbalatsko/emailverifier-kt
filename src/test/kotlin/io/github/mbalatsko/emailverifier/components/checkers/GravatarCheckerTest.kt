package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.VerificationError
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
    @Test
    fun `getGravatarUrl returns URL for existing gravatar`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("somehash", HttpStatusCode.OK)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertNotNull(checker.getGravatarUrl("test@example.com"))
        }

    @Test
    fun `getGravatarUrl returns null for non-existent gravatar`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("", HttpStatusCode.NotFound)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertNull(checker.getGravatarUrl("test@example.com"))
        }

    @Test
    fun `getGravatarUrl throws VerificationError on 4xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.BadRequest)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertFailsWith<VerificationError> {
                checker.getGravatarUrl("test@example.com")
            }
        }

    @Test
    fun `getGravatarUrl throws VerificationError on 5xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.InternalServerError)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)
            assertFailsWith<VerificationError> {
                checker.getGravatarUrl("test@example.com")
            }
        }
}
