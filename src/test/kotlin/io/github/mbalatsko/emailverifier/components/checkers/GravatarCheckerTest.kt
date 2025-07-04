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
import kotlin.test.assertTrue

class GravatarCheckerTest {
    @Test
    fun `hasGravatar returns true on first attempt`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("somehash", HttpStatusCode.OK)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)

            assertTrue(checker.hasGravatar("test@example.com"))
        }

    @Test
    fun `hasGravatar throws VerificationError on 5xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.InternalServerError)
                }
            val client = HttpClient(mockEngine)
            val checker = GravatarChecker(client)

            assertFailsWith<VerificationError> {
                checker.hasGravatar("test@example.com")
            }
        }
}
