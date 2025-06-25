package io.github.mbalatsko.emailverifier.components.providers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class DomainProvidersTest {
    private class TestLFDomainsProvider(private val rawData: String) : LFDomainsProvider() {
        override suspend fun obtainData(): String = rawData
    }

    @Test
    fun `provide filters and normalizes correctly`() = runTest {
        val rawInput = """
        // comment
        example.com
        EXAMPLE.ORG  
        bücher.de
        """.trimIndent()

        val provider = TestLFDomainsProvider(rawInput)
        val result = provider.provide()

        assertEquals(
            listOf("example.com", "example.org", "xn--bcher-kva.de"),
            result
        )
    }

    @Test
    fun `online provider returns expected data`() = runTest {
        val mockEngine = MockEngine {
            respond("disposable.com\ntrashmail.org", headers = headersOf("Content-Type" to listOf("text/plain")))
        }

        val client = HttpClient(mockEngine)
        val provider = OnlineLFDomainsProvider("https://mock.url", client)

        val result = provider.provide()
        assertEquals(listOf("disposable.com", "trashmail.org"), result)
    }
}
