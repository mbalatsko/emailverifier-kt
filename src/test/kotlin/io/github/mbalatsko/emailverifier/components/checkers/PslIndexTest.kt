package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PslIndexTest {
    private class TestDomainsProvider(
        private val rules: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = rules
    }

    @Test
    fun `findRegistrableDomain with simple suffix rules`() =
        runTest {
            val rules = setOf("com", "co.uk")
            val psl = PslIndex.init(TestDomainsProvider(rules))

            assertEquals("example.com", psl.findRegistrableDomain("example.com"))
            assertNull(psl.findRegistrableDomain("com"))
            assertEquals("foo.co.uk", psl.findRegistrableDomain("foo.co.uk"))
            assertEquals("foo.co.uk", psl.findRegistrableDomain("bar.foo.co.uk"))
            assertNull(psl.findRegistrableDomain("co.uk"))
        }

    @Test
    fun `findRegistrableDomain with wildcard rules`() =
        runTest {
            val rules = setOf("*.ck")
            val psl = PslIndex.init(TestDomainsProvider(rules))

            assertNull(psl.findRegistrableDomain("a.ck"))
            assertEquals("b.a.ck", psl.findRegistrableDomain("b.a.ck"))
        }

    @Test
    fun `findRegistrableDomain with exception rules`() =
        runTest {
            val rules = setOf("*.ck", "!pref.ck")
            val psl = PslIndex.init(TestDomainsProvider(rules))

            assertNull(psl.findRegistrableDomain("foo.ck"))
            assertEquals("pref.ck", psl.findRegistrableDomain("pref.ck"))
            assertEquals("pref.ck", psl.findRegistrableDomain("b.pref.ck"))
        }

    @Test
    fun `findRegistrableDomain with no matching rule`() =
        runTest {
            val rules = setOf("net")
            val psl = PslIndex.init(TestDomainsProvider(rules))
            assertNull(psl.findRegistrableDomain("example.org"))
        }

    @Test
    fun `findRegistrableDomain with complex scenarios`() =
        runTest {
            val rules = setOf("*.platform.sh", "!w3.platform.sh", "com.ac")
            val psl = PslIndex.init(TestDomainsProvider(rules))

            assertEquals("w3.platform.sh", psl.findRegistrableDomain("w3.platform.sh"))
            assertEquals("w3.platform.sh", psl.findRegistrableDomain("sub.w3.platform.sh"))
            assertNull(psl.findRegistrableDomain("platform.sh"))
            assertNull(psl.findRegistrableDomain("example.platform.sh"))
            assertEquals("sub.example.platform.sh", psl.findRegistrableDomain("sub.example.platform.sh"))
            assertEquals("example.com.ac", psl.findRegistrableDomain("example.com.ac"))
            assertEquals("example.com.ac", psl.findRegistrableDomain("sub.example.com.ac"))
        }
}
