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

class UsernameInDatasetCheckerTest {
    private class TestDomainsProvider(
        private val domains: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = domains
    }

    private val testChecker =
        runBlocking {
            UsernameInDatasetChecker.create(
                TestDomainsProvider(setOf("admin", "support")),
            )
        }

    private val testEmail = EmailParts("email", "", "test.com")

    @Test
    fun `check returns true for role-based usernames`() =
        runTest {
            var result = testChecker.check(testEmail.copy(username = "admin"), Unit)
            assertTrue(result.match)
            assertEquals("admin", result.matchedOn)

            result = testChecker.check(testEmail.copy(username = "support"), Unit)
            assertTrue(result.match)
            assertEquals("support", result.matchedOn)
        }

    @Test
    fun `check returns false for non-role-based usernames`() =
        runTest {
            val result = testChecker.check(testEmail.copy(username = "john.doe"), Unit)
            assertFalse(result.match)
            assertNull(result.matchedOn)
        }
}
