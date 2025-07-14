package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostnameInDatasetCheckerTest {
    private class TestDomainsProvider(
        private val domains: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = domains
    }

    private val testDomainsDataset =
        setOf(
            "temp-mail.org",
            "mailinator.com",
            "disposable.co",
        )

    private val testChecker =
        runBlocking {
            HostnameInDatasetChecker.create(TestDomainsProvider(testDomainsDataset))
        }

    private val testEmail = EmailParts("test", "", "test.com")

    @Test
    fun `check returns true for exact match`() =
        runTest {
            var result = testChecker.check(testEmail.copy(hostname = "temp-mail.org"), Unit)
            assertTrue(result.match)
            assertEquals("temp-mail.org", result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "mailinator.com"), Unit)
            assertTrue(result.match)
            assertEquals("mailinator.com", result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "disposable.co"), Unit)
            assertTrue(result.match)
            assertEquals("disposable.co", result.matchedOn)
        }

    @Test
    fun `check returns true for subdomains of disposable domains`() =
        runTest {
            var result = testChecker.check(testEmail.copy(hostname = "foo.temp-mail.org"), Unit)
            assertTrue(result.match)
            assertEquals("temp-mail.org", result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "bar.foo.mailinator.com"), Unit)
            assertTrue(result.match)
            assertEquals("mailinator.com", result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "x.y.disposable.co"), Unit)
            assertTrue(result.match)
            assertEquals("disposable.co", result.matchedOn)
        }

    @Test
    fun `check returns false for non-disposable domains`() =
        runTest {
            var result = testChecker.check(testEmail.copy(hostname = "example.com"), Unit)
            assertFalse(result.match)
            assertNull(result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "foo.example.org"), Unit)
            assertFalse(result.match)
            assertNull(result.matchedOn)

            result = testChecker.check(testEmail.copy(hostname = "co"), Unit) // single label
            assertFalse(result.match)
            assertNull(result.matchedOn)
        }
}
