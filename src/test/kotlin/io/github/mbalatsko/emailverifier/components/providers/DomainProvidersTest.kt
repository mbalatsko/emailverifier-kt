package io.github.mbalatsko.emailverifier.components.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainProvidersTest {
    private class TestLFDomainsProvider(
        private val rawData: String,
    ) : LFDomainsProvider() {
        override suspend fun obtainData(): String = rawData
    }

    @Test
    fun `provide filters and normalizes correctly`() =
        runTest {
            val rawInput =
                """
                // comment
                example.com
                EXAMPLE.ORG
                bücher.de
                """.trimIndent()

            val provider = TestLFDomainsProvider(rawInput)
            val result = provider.provide()

            assertEquals(
                setOf("example.com", "example.org", "xn--bcher-kva.de"),
                result,
            )
        }

    @Test
    fun `online provider returns expected data`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("disposable.com\ntrashmail.org", headers = headersOf("Content-Type" to listOf("text/plain")))
                }

            val client = HttpClient(mockEngine)
            val provider = OnlineLFDomainsProvider("https://mock.url", client)

            val result = provider.provide()
            assertEquals(setOf("disposable.com", "trashmail.org"), result)
        }

    @Test
    fun `offline provider returns expected data`() =
        runTest {
            val provider = OfflineLFDomainsProvider("/offline-data/disposable.txt")
            val result = provider.provide()
            assertTrue(result.contains("yopmail.com"))
            assertTrue(result.contains("simplelogin.com"))
        }

    @Test
    fun `offline provider throws for non-existent resource`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            OfflineLFDomainsProvider("non-existent-file.txt")
        }
    }
}
