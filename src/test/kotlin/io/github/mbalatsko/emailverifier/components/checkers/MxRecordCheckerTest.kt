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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleDoHLookupBackendTest {
    private fun mockClient(
        responseJson: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = responseJson,
                    status = status,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        }
    }

    @Test
    fun `getMxRecords returns sorted records`() =
        runTest {
            val json =
                """
                {
                  "Answer": [
                    { "name":"example.com.","type":15,"data":"20 mx2.example.com." },
                    { "name":"example.com.","type":15,"data":"10 mx1.example.com." }
                  ]
                }
                """.trimIndent()
            val backend = GoogleDoHLookupBackend(httpClient = mockClient(json))
            val records = backend.getMxRecords("example.com")
            assertEquals(
                listOf(
                    MxRecord("mx2.example.com", 20),
                    MxRecord("mx1.example.com", 10),
                ),
                records,
            )
        }

    @Test
    fun `getMxRecords returns empty list for no MX records`() =
        runTest {
            val json = """{ "Answer": [] }"""
            val backend = GoogleDoHLookupBackend(httpClient = mockClient(json))
            assertTrue(backend.getMxRecords("example.org").isEmpty())
        }

    @Test
    fun `getMxRecords filters non-MX records`() =
        runTest {
            val json =
                """
                {
                  "Answer": [
                    { "name":"example.com.","type":1,"data":"1.2.3.4" }
                  ]
                }
                """.trimIndent()
            val backend = GoogleDoHLookupBackend(httpClient = mockClient(json))
            assertTrue(backend.getMxRecords("example.com").isEmpty())
        }

    @Test
    fun `getMxRecords throws VerificationError on 5xx error`() =
        runTest {
            val mockEngine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val client = HttpClient(mockEngine)
            val backend = GoogleDoHLookupBackend(client)
            assertFailsWith<VerificationError> {
                backend.getMxRecords("example.com")
            }
        }
}

class MxRecordCheckerTest {
    private class FakeDnsBackend(
        private val records: List<MxRecord>,
    ) : DnsLookupBackend {
        override suspend fun getMxRecords(hostname: String): List<MxRecord> = records
    }

    @Test
    fun `getRecords returns records from backend`() =
        runTest {
            val expectedRecords = listOf(MxRecord("mx.example.com", 10))
            val checker = MxRecordChecker(FakeDnsBackend(expectedRecords))
            assertEquals(expectedRecords, checker.getRecords("any.domain"))
        }
}
