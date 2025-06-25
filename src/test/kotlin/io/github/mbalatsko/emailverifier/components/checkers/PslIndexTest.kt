package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PslIndexTest {
    private class TestDomainsProvider(
        private val rules: List<String>,
    ) : DomainsProvider {
        override suspend fun provide(): List<String> = rules
    }

    @Test
    fun `basic registrability with simple suffix rules`() =
        runTest {
            val rules = listOf("com", "co.uk")
            val psl = PslIndex(TestDomainsProvider(rules))
            psl.build()

            assertTrue(psl.isHostnameRegistrable("example.com"), "example.com should be registrable")
            assertFalse(psl.isHostnameRegistrable("com"), "com should not be registrable")

            assertTrue(psl.isHostnameRegistrable("foo.co.uk"), "foo.co.uk should be registrable")
            assertFalse(psl.isHostnameRegistrable("co.uk"), "co.uk should not be registrable")
        }

    @Test
    fun `wildcard rules block second-level but allow deeper domains`() =
        runTest {
            val rules = listOf("*.ck")
            val psl = PslIndex(TestDomainsProvider(rules))
            psl.build()

            assertFalse(psl.isHostnameRegistrable("a.ck"), "a.ck should not be registrable under *.ck")
            assertTrue(psl.isHostnameRegistrable("b.a.ck"), "b.a.ck should be registrable under *.ck")
        }

    @Test
    fun `exception rules override suffix rules`() =
        runTest {
            val rules = listOf("*.ck", "!pref.ck")
            val psl = PslIndex(TestDomainsProvider(rules))
            psl.build()

            assertFalse(psl.isHostnameRegistrable("foo.ck"), "foo.ck should not be registrable under *.ck")
            // pref.ck is an exception, so it becomes registrable
            assertTrue(psl.isHostnameRegistrable("pref.ck"), "pref.ck should be registrable due to exception")
            // But its subdomains still follow the wildcard rule
            assertTrue(psl.isHostnameRegistrable("sub.pref.ck"), "sub.pref.ck should be registrable as deeper domain")
        }

    @Test
    fun `no match means non-registrable`() =
        runTest {
            val rules = listOf("net")
            val psl = PslIndex(TestDomainsProvider(rules))
            psl.build()

            // no rule for 'org'
            assertFalse(psl.isHostnameRegistrable("example.org"), "example.org should not be registrable without matching rule")
        }
}
