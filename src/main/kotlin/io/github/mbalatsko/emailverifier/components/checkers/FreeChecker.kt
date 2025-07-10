package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.Constants
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
    domainsProvider: DomainsProvider,
) : BaseChecker(domainsProvider) {
    /**
     * Determines if the specified hostname is in the free-email provider list.
     *
     * @param hostname the domain part of an email address.
     * @return `true` if the domain is recognized as a free-email provider.
     */
    fun isFree(hostname: String): Boolean = hostname in dataSet

    companion object {
        /**
         * URL pointing to a text file of free email provider domains.
         */
        @Suppress("ktlint:standard:max-line-length")
        const val FREE_EMAILS_LIST_URL = "https://gist.githubusercontent.com/okutbay/5b4974b70673dfdcc21c517632c1f984/raw/daa988474b832059612f1b2468fba6cfcd2390dd/free_email_provider_domains.txt"

        const val FREE_EMAILS_LIST_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/free.txt"

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
