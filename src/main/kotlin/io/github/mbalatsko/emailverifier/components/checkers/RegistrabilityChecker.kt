package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import org.slf4j.LoggerFactory

/**
 * Data class holding the registrable domain found during the registrability check.
 * @property registrableDomain The registrable domain string, or null if not found.
 */
data class RegistrabilityData(
    val registrableDomain: String?,
)

/**
 * Check registrability of email hostname
 * Uses Public Suffix List (PSL) index for determining registrability of hostnames.
 *
 * Parses rules from a [DomainsProvider], typically using the PSL format from publicsuffix.org.
 * Supports normal, wildcard (`*.`), and exception (`!`) rules per PSL specification.
 */
class RegistrabilityChecker(
    val domainsProvider: DomainsProvider,
) : IChecker<RegistrabilityData, Unit> {
    /**
     * Internal node structure for PSL trie.
     *
     * @property children child labels of the current node.
     * @property isSuffix true if this node represents a terminal suffix rule.
     * @property isException true if this rule is an exception (starts with `!` in PSL).
     * @property isWildcard true if this rule is a wildcard rule (e.g. `*.example`).
     */
    private class Node(
        val children: MutableMap<String, Node> = mutableMapOf(),
        var isSuffix: Boolean = false,
        var isException: Boolean = false,
        var isWildcard: Boolean = false,
    )

    private var root = Node()

    /**
     * Rebuilds the PSL index by fetching and parsing rules from [domainsProvider].
     * Must be called before using [check].
     */
    suspend fun build() {
        logger.debug("Building PSL index from {}...", domainsProvider::class.java.simpleName)
        root = Node()
        val rules = domainsProvider.provide()
        rules.forEach { add(it) }
        logger.debug("PSL index built with {} rules.", rules.size)
    }

    /**
     * Adds a single rule from the Public Suffix List to the internal trie.
     *
     * Handles exception (`!`) and wildcard (`*.`) rules as defined in the PSL format.
     *
     * @param rule a single line from the PSL data source.
     */
    fun add(rule: String) {
        var exception = false
        var wildcard = false
        var ruleStr = rule.trim().lowercase()
        if (ruleStr.startsWith("!")) {
            exception = true
            ruleStr = ruleStr.substring(1)
        }
        val labels = ruleStr.split(".").reversed()
        var node = root
        for (label in labels) {
            if (label == "*") {
                wildcard = true
                continue
            }
            node = node.children.getOrPut(label) { Node() }
        }
        if (wildcard) {
            node.children["*"] = Node(isSuffix = true, isWildcard = true)
        } else {
            node.isSuffix = true
        }
        if (exception) {
            node.isException = true
        }
    }

    /**
     * Finds the registrable domain for a given hostname based on PSL rules.
     *
     * A registrable domain is the portion of a domain name that is not part of a public suffix.
     * For example, for "www.example.co.uk", "example.co.uk" is the registrable domain.
     *
     * @param hostname the full hostname to check (e.g., "www.example.co.uk").
     * @return the registrable domain as a string, or null if the hostname itself is a public suffix
     *   or cannot be determined (e.g., a TLD).
     */
    private fun findRegistrableDomain(hostname: String): String? {
        val labels =
            hostname
                .split(".")
                .reversed()
        if (labels.size <= 1) {
            logger.trace("Hostname {} is a TLD, not registrable.", hostname)
            return null // TLDs are not registrable
        }

        var node = root

        var matchLen: Int? = null
        var eTld: String? = null
        for ((i, label) in labels.withIndex()) {
            val next = node.children[label] ?: node.children["*"]
            eTld = if (eTld != null) "$label.$eTld" else label
            if (next == null) break
            node = next

            if (node.isException) {
                logger.trace("Found exception rule for {}, registrable domain is {}.", hostname, eTld)
                return eTld
            } else if (node.isSuffix || node.isWildcard) {
                matchLen = i + 1
            }
        }
        val registrableDomain = if (matchLen != null && labels.size > matchLen) eTld else null
        logger.trace("For hostname {}, found registrable domain: {}", hostname, registrableDomain)
        return registrableDomain
    }

    /**
     * Checks the registrability of the email's hostname.
     *
     * @param email the decomposed parts of the email address to check.
     * @param context (not used)
     * @return a [RegistrabilityData] object containing the registrable domain if found.
     */
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): RegistrabilityData =
        RegistrabilityData(
            findRegistrableDomain(email.hostname),
        )

    companion object {
        private val logger = LoggerFactory.getLogger(RegistrabilityChecker::class.java)

        /** Default URL to Mozilla-maintained Public Suffix List. */
        const val MOZILLA_PSL_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

        const val MOZILLA_PSL_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/psl.txt"

        /**
         * Creates and initializes a [RegistrabilityChecker] using the given provider.
         *
         * @param domainsProvider source of PSL rules.
         * @return an initialized [RegistrabilityChecker].
         */
        suspend fun create(domainsProvider: DomainsProvider): RegistrabilityChecker {
            logger.debug("Creating RegistrabilityChecker...")
            val index = RegistrabilityChecker(domainsProvider)
            index.build()
            logger.debug("RegistrabilityChecker created.")
            return index
        }
    }
}
