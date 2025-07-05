package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleBasedUsernameCheckerTest {
    private class TestDomainsProvider(
        private val domains: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = domains
    }

    @Test
    fun `isRoleBased returns true for role-based usernames`() =
        runTest {
            val provider = TestDomainsProvider(setOf("admin", "support"))
            val checker = RoleBasedUsernameChecker.init(provider)

            assertTrue(checker.isRoleBased("admin"))
            assertTrue(checker.isRoleBased("support"))
        }

    @Test
    fun `isRoleBased returns false for non-role-based usernames`() =
        runTest {
            val provider = TestDomainsProvider(setOf("admin", "support"))
            val checker = RoleBasedUsernameChecker.init(provider)

            assertFalse(checker.isRoleBased("john.doe"))
        }
}
