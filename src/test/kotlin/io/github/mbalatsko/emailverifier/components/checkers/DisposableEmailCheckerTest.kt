package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisposableEmailCheckerTest {
    private class TestDomainsProvider(
        private val domains: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = domains
    }

    private val testDomains =
        setOf(
            "temp-mail.org",
            "mailinator.com",
            "disposable.co",
        )

    @Test
    fun `isDisposable returns true for exact match`() =
        runTest {
            val provider = TestDomainsProvider(testDomains)
            val checker = DisposableEmailChecker(provider)
            checker.loadData()

            assertTrue(checker.isDisposable("temp-mail.org"))
            assertTrue(checker.isDisposable("mailinator.com"))
            assertTrue(checker.isDisposable("disposable.co"))
        }

    @Test
    fun `isDisposable returns true for subdomains of disposable domains`() =
        runTest {
            val provider = TestDomainsProvider(testDomains)
            val checker = DisposableEmailChecker(provider)
            checker.loadData()

            assertTrue(checker.isDisposable("foo.temp-mail.org"))
            assertTrue(checker.isDisposable("bar.foo.mailinator.com"))
            assertTrue(checker.isDisposable("x.y.disposable.co"))
        }

    @Test
    fun `isDisposable returns false for non-disposable domains`() =
        runTest {
            val provider = TestDomainsProvider(testDomains)
            val checker = DisposableEmailChecker(provider)
            checker.loadData()

            assertFalse(checker.isDisposable("example.com"))
            assertFalse(checker.isDisposable("foo.example.org"))
            assertFalse(checker.isDisposable("co")) // single label
        }

    @Test
    fun `init companion correctly loads data`() =
        runTest {
            val checker = DisposableEmailChecker.Companion.init(TestDomainsProvider(testDomains))
            // After init, data should be loaded and isDisposable should work
            assertTrue(checker.isDisposable("temp-mail.org"))
            assertFalse(checker.isDisposable("example.com"))
        }
}
