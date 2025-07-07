package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Detects whether a given email hostname is associated with disposable email services.
 *
 * Accepts a [DomainsProvider] that supplies a list of known disposable domains,
 * typically from public blacklists such as `disposable-email-domains`.
 */
class DisposableEmailChecker(
    val domainsProvider: DomainsProvider,
) {
    private var disposableDomainsSet = emptySet<String>()

    /**
     * Loads and indexes the disposable domain list from the [domainsProvider].
     *
     * Must be called before using [isDisposable].
     */
    suspend fun loadData() {
        disposableDomainsSet = domainsProvider.provide()
    }

    /**
     * Determines if the given hostname or any of its parent domains (down to second level)
     * are listed as disposable.
     *
     * For example, if `mail.temp-mail.org` is checked and `temp-mail.org` is in the list,
     * this method returns `true`.
     *
     * @param hostname the domain part of an email address.
     * @return `true` if the domain is disposable, `false` otherwise.
     */
    fun isDisposable(hostname: String): Boolean {
        val labels = hostname.split('.')

        // Check all subdomains of given hostname up to level 2 (e.g. google.com)
        for (i in 0..labels.size - 2) {
            val partialHostname = labels.slice(i..labels.size - 1).joinToString(".")
            if (partialHostname in disposableDomainsSet) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * URL of the strict disposable email domain list and e-mail services which do allow anonymous signup.
         * See https://github.com/disposable/disposable?tab=readme-ov-file#strict-mode for details.
         */
        const val DISPOSABLE_EMAILS_LIST_STRICT_URL =
            "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains_strict.txt"

        const val DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/disposable.txt"

        /**
         * URL of the general disposable email domain list.
         * See https://github.com/disposable/disposable?tab=readme-ov-file#normal-mode for details.
         */
        const val DISPOSABLE_EMAILS_LIST_NORMAL_URL =
            "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains.txt"

        suspend fun init(domainsProvider: DomainsProvider): DisposableEmailChecker {
            val disposableEmailChecker = DisposableEmailChecker(domainsProvider)
            disposableEmailChecker.loadData()
            return disposableEmailChecker
        }
    }
}
