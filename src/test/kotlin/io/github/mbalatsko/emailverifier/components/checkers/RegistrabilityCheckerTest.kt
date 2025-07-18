package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistrabilityCheckerTest {
    private class TestDomainsProvider(
        private val rules: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = rules
    }

    private val testEmail = EmailParts("test", "", "test.com")

    @Test
    fun `check with simple suffix rules`() =
        runTest {
            val rules = setOf("com", "co.uk")
            val registrabilityChecker = RegistrabilityChecker.create(TestDomainsProvider(rules))

            assertEquals(
                "example.com",
                registrabilityChecker.check(testEmail.copy(hostname = "example.com"), Unit).registrableDomain,
            )
            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "com"), Unit).registrableDomain)
            assertEquals(
                "foo.co.uk",
                registrabilityChecker.check(testEmail.copy(hostname = "foo.co.uk"), Unit).registrableDomain,
            )
            assertEquals(
                "foo.co.uk",
                registrabilityChecker.check(testEmail.copy(hostname = "bar.foo.co.uk"), Unit).registrableDomain,
            )
            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "co.uk"), Unit).registrableDomain)
        }

    @Test
    fun `check with wildcard rules`() =
        runTest {
            val rules = setOf("*.ck")
            val registrabilityChecker = RegistrabilityChecker.create(TestDomainsProvider(rules))

            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "a.ck"), Unit).registrableDomain)
            assertEquals(
                "b.a.ck",
                registrabilityChecker.check(testEmail.copy(hostname = "b.a.ck"), Unit).registrableDomain,
            )
        }

    @Test
    fun `check with exception rules`() =
        runTest {
            val rules = setOf("*.ck", "!pref.ck")
            val registrabilityChecker = RegistrabilityChecker.create(TestDomainsProvider(rules))

            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "foo.ck"), Unit).registrableDomain)
            assertEquals(
                "pref.ck",
                registrabilityChecker.check(testEmail.copy(hostname = "pref.ck"), Unit).registrableDomain,
            )
            assertEquals(
                "pref.ck",
                registrabilityChecker.check(testEmail.copy(hostname = "b.pref.ck"), Unit).registrableDomain,
            )
        }

    @Test
    fun `check with no matching rule`() =
        runTest {
            val rules = setOf("net")
            val registrabilityChecker = RegistrabilityChecker.create(TestDomainsProvider(rules))
            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "example.org"), Unit).registrableDomain)
        }

    @Test
    fun `check with complex scenarios`() =
        runTest {
            val rules = setOf("*.platform.sh", "!w3.platform.sh", "com.ac")
            val registrabilityChecker = RegistrabilityChecker.create(TestDomainsProvider(rules))

            assertEquals(
                "w3.platform.sh",
                registrabilityChecker.check(testEmail.copy(hostname = "w3.platform.sh"), Unit).registrableDomain,
            )
            assertEquals(
                "w3.platform.sh",
                registrabilityChecker.check(testEmail.copy(hostname = "sub.w3.platform.sh"), Unit).registrableDomain,
            )
            assertNull(registrabilityChecker.check(testEmail.copy(hostname = "platform.sh"), Unit).registrableDomain)
            assertNull(
                registrabilityChecker
                    .check(
                        testEmail.copy(hostname = "example.platform.sh"),
                        Unit,
                    ).registrableDomain,
            )
            assertEquals(
                "sub.example.platform.sh",
                registrabilityChecker
                    .check(
                        testEmail.copy(hostname = "sub.example.platform.sh"),
                        Unit,
                    ).registrableDomain,
            )
            assertEquals(
                "example.com.ac",
                registrabilityChecker.check(testEmail.copy(hostname = "example.com.ac"), Unit).registrableDomain,
            )
            assertEquals(
                "example.com.ac",
                registrabilityChecker.check(testEmail.copy(hostname = "sub.example.com.ac"), Unit).registrableDomain,
            )
        }
}
