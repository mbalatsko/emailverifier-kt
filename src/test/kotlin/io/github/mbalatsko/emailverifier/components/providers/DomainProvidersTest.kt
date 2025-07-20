package io.github.mbalatsko.emailverifier.components.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `online provider returns empty set for empty response`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("", headers = headersOf("Content-Type" to listOf("text/plain")))
                }

            val client = HttpClient(mockEngine)
            val provider = OnlineLFDomainsProvider("https://mock.url", client)

            val result = provider.provide()
            assertTrue(result.isEmpty())
        }

    @Test
    fun `resource file provider returns expected data`() =
        runTest {
            val provider = ResourceFileLFDomainsProvider("/test.txt")
            val result = provider.provide()
            assertTrue(result.contains("yopmail.com"))
            assertTrue(result.contains("simplelogin.com"))
        }

    @Test
    fun `resource file provider throws for non-existent resource`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ResourceFileLFDomainsProvider("non-existent-file.txt")
        }
    }

    @Test
    fun `file provider returns expected data`() =
        runTest {
            val tempFile = createTempFile(prefix = "domains", suffix = ".txt")
            tempFile.writeText("domain.com")

            val provider = FileLFDomainsProvider(tempFile.absolutePathString())
            val result = provider.provide()
            assertTrue(result.contains("domain.com"))

            // cleanup
            tempFile.deleteExisting()
        }

    @Test
    fun `file file provider throws for non-existent resource`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            FileLFDomainsProvider("/non-existent-file.txt")
        }
    }
}
