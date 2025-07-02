package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Checks whether a given username is a role-based address (e.g., `info`, `admin`, `support`).
 *
 * Loads a curated list of known role-based local-parts from a [DomainsProvider]
 * and allows rapid membership queries.
 *
 * @property domainsProvider source of role-based usernames.
 */
class RoleBasedUsernameChecker(
    val domainsProvider: DomainsProvider,
) {
    private var roleBasedUsernamesSet = emptySet<String>()

    /**
     * Loads and indexes the role-based username list from the [domainsProvider].
     *
     * Must be called before invoking [isRoleBased].
     */
    suspend fun loadData() {
        roleBasedUsernamesSet = domainsProvider.provide()
    }

    /**
     * Determines if the specified username is recognized as role-based.
     *
     * @param username the username of an email address.
     * @return `true` if the username is in the role-based list.
     */
    fun isRoleBased(username: String): Boolean = username in roleBasedUsernamesSet

    companion object {
        /**
         * URL pointing to a text file of common role-based usernames.
         */
        const val ROLE_BASED_USERNAMES_LIST_URL =
            "https://raw.githubusercontent.com/mbalatsko/role-based-email-addresses-list/main/list.txt"

        /**
         * Convenience initializer that creates a [RoleBasedUsernameChecker] and immediately
         * loads its username data.
         *
         * @param domainsProvider the provider for role-based usernames.
         * @return an initialized [RoleBasedUsernameChecker].
         */
        suspend fun init(domainsProvider: DomainsProvider): RoleBasedUsernameChecker {
            val roleBasedUsernameChecker = RoleBasedUsernameChecker(domainsProvider)
            roleBasedUsernameChecker.loadData()
            return roleBasedUsernameChecker
        }
    }
}
