package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Checks whether a hostname belongs to a known “free” email provider.
 *
 * Loads a list of free email domains (e.g., gmail.com, yahoo.com) from a
 * [DomainsProvider] and allows rapid membership queries.
 *
 * @property domainsProvider source of domain names.
 */
class FreeChecker(
    val domainsProvider: DomainsProvider,
) {
    private var freeDomainsSet = emptySet<String>()

    /**
     * Loads and indexes the free-email domain list from the [domainsProvider].
     *
     * Must be called before invoking [isFree].
     */
    suspend fun loadData() {
        freeDomainsSet = domainsProvider.provide()
    }

    /**
     * Determines if the specified hostname is in the free-email provider list.
     *
     * @param hostname the domain part of an email address.
     * @return `true` if the domain is recognized as a free-email provider.
     */
    fun isFree(hostname: String): Boolean = hostname in freeDomainsSet

    companion object {
        /**
         * URL pointing to a text file of free email provider domains.
         */
        @Suppress("ktlint:standard:max-line-length")
        const val FREE_EMAILS_LIST_URL = "https://gist.githubusercontent.com/okutbay/5b4974b70673dfdcc21c517632c1f984/raw/daa988474b832059612f1b2468fba6cfcd2390dd/free_email_provider_domains.txt"

        /**
         * Convenience initializer that creates a [FreeChecker] and immediately
         * loads its domain data.
         *
         * @param domainsProvider the provider for free email domains.
         * @return an initialized [FreeChecker].
         */
        suspend fun init(domainsProvider: DomainsProvider): FreeChecker {
            val freeEmailChecker = FreeChecker(domainsProvider)
            freeEmailChecker.loadData()
            return freeEmailChecker
        }
    }
}
