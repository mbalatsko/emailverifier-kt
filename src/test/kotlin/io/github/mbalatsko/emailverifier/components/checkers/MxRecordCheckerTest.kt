package io.github.mbalatsko.emailverifier.components.checkers
import io.github.mbalatsko.emailverifier.VerificationError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleDoHLookupBackendTest {
    private fun mockClient(
        responseJson: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = responseJson,
                    status = status,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        }
    }

    @Test
    fun `hasMxRecords returns true when Answer array contains MX entries`() =
        runTest {
            val json =
                """
                {
                  "Answer": [
                    { "name":"example.com.","type":15,"data":"mx1.example.com." },
                    { "name":"example.com.","type":15,"data":"mx2.example.com." }
                  ]
                }
                """.trimIndent()

            val backend =
                GoogleDoHLookupBackend(
                    baseURL = "https://dns.google/resolve",
                    httpClient = mockClient(json),
                )

            assertTrue(backend.hasMxRecords("example.com"))
        }

    @Test
    fun `hasMxRecords returns false when Answer array is empty`() =
        runTest {
            val json = """{ "Answer": [] }"""
            val backend =
                GoogleDoHLookupBackend(
                    httpClient = mockClient(json),
                )
            assertFalse(backend.hasMxRecords("example.org"))
        }

    @Test
    fun `hasMxRecords returns false when Answer contains non-MX types`() =
        runTest {
            val json =
                """
                {
                  "Answer": [
                    { "name":"example.com.","type":1,"data":"1.2.3.4" }
                  ]
                }
                """.trimIndent()

            val backend =
                GoogleDoHLookupBackend(
                    httpClient = mockClient(json),
                )
            assertFalse(backend.hasMxRecords("example.com"))
        }

    @Test
    fun `hasMxRecords throws VerificationError on 5xx error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respondError(HttpStatusCode.InternalServerError)
                }
            val client = HttpClient(mockEngine)
            val backend = GoogleDoHLookupBackend(client)

            assertFailsWith<VerificationError> {
                backend.hasMxRecords("example.com")
            }
        }
}

class MxRecordCheckerTest {
    private class FakeDnsBackend(
        private val hasMx: Boolean,
    ) : DnsLookupBackend {
        override suspend fun hasMxRecords(hostname: String): Boolean = hasMx
    }

    @Test
    fun `isPresent returns true when backend has MX`() =
        runTest {
            val checker = MxRecordChecker(FakeDnsBackend(true))
            assertTrue(checker.isPresent("any.domain"))
        }

    @Test
    fun `isPresent returns false when backend has no MX`() =
        runTest {
            val checker = MxRecordChecker(FakeDnsBackend(false))
            assertFalse(checker.isPresent("any.domain"))
        }
}
