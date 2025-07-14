package io.github.mbalatsko.emailverifier.components

/**
 * Object containing various constants used throughout the email verification library.
 */
object Constants {
    /**
     * The path within resources where offline data files are stored.
     */
    const val OFFLINE_DATA_PATH = "/offline-data"
    const val DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/disposable.txt"
    const val FREE_EMAILS_LIST_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/free.txt"
    const val ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/role-based.txt"

    /**
     * URL of the strict disposable email domain list and e-mail services which do allow anonymous signup.
     * See https://github.com/disposable/disposable?tab=readme-ov-file#strict-mode for details.
     */
    const val DISPOSABLE_EMAILS_LIST_STRICT_URL =
        "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains_strict.txt"

    /**
     * URL of the general disposable email domain list.
     * See https://github.com/disposable/disposable?tab=readme-ov-file#normal-mode for details.
     */
    const val DISPOSABLE_EMAILS_LIST_NORMAL_URL =
        "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains.txt"

    /**
     * URL pointing to a text file of free email provider domains.
     */
    @Suppress("ktlint:standard:max-line-length")
    const val FREE_EMAILS_LIST_URL = "https://gist.githubusercontent.com/okutbay/5b4974b70673dfdcc21c517632c1f984/raw/daa988474b832059612f1b2468fba6cfcd2390dd/free_email_provider_domains.txt"

    /**
     * URL pointing to a text file of common role-based usernames.
     */
    const val ROLE_BASED_USERNAMES_LIST_URL =
        "https://raw.githubusercontent.com/mbalatsko/role-based-email-addresses-list/main/list.txt"
}
