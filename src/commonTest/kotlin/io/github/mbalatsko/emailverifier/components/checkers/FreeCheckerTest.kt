package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreeCheckerTest {
    private class TestDomainsProvider(
        private val domains: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = domains
    }

    @Test
    fun `isFree returns true for free domains`() =
        runTest {
            val provider = TestDomainsProvider(setOf("gmail.com", "yahoo.com"))
            val checker = FreeChecker.init(provider)

            assertTrue(checker.isFree("gmail.com"))
            assertTrue(checker.isFree("yahoo.com"))
        }

    @Test
    fun `isFree returns false for non-free domains`() =
        runTest {
            val provider = TestDomainsProvider(setOf("gmail.com", "yahoo.com"))
            val checker = FreeChecker.init(provider)

            assertFalse(checker.isFree("example.com"))
        }
}
