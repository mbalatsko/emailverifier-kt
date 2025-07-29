package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Parses rules from a [domainsProvider], typically using the PSL format from publicsuffix.org.
 * Supports normal, wildcard (`*.`), and exception (`!`) rules per PSL specification.
 *
 * Also [customRules] could be specified to extend rules from [DomainsProvider]
 */
class RegistrabilityChecker(
    private val domainsProvider: DomainsProvider,
    private val customRules: Set<String> = emptySet(),
) : IChecker<RegistrabilityData, Unit>,
    Refreshable {
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
    private val mutex = Mutex()

    /**
     * Rebuilds the PSL index by fetching and parsing rules from [domainsProvider].
     * Must be called before using [check].
     */
    override suspend fun refresh() {
        logger.debug("Building PSL index from {}...", domainsProvider::class.java.simpleName)
        val newRoot = Node()
        val rules = domainsProvider.provide()
        rules.forEach { addToNode(newRoot, it) }
        logger.debug("PSL index built with {} rules.", rules.size)

        if (customRules.isNotEmpty()) {
            logger.debug("Adding {} custom PSL rules.", customRules.size)
            customRules.forEach {
                addToNode(newRoot, it)
            }
        }

        mutex.withLock {
            root = newRoot
        }
    }

    /**
     * Adds a single rule from the Public Suffix List to the internal trie.
     *
     * Handles exception (`!`) and wildcard (`*.`) rules as defined in the PSL format.
     *
     * @param rule a single line from the PSL data source.
     */
    private fun addToNode(
        node: Node,
        rule: String,
    ) {
        if (!pslRuleRegex.matches(rule.trim())) {
            logger.error("Ignoring invalid PSL rule: {}", rule)
            return
        }

        var exception = false
        var wildcard = false
        var ruleStr = rule.trim().lowercase()
        if (ruleStr.startsWith("!")) {
            exception = true
            ruleStr = ruleStr.substring(1)
        }
        val labels = ruleStr.split(".").reversed()
        var currNode = node
        for (label in labels) {
            if (label == "*") {
                wildcard = true
                continue
            }
            currNode = currNode.children.getOrPut(label) { Node() }
        }
        if (wildcard) {
            currNode.children["*"] = Node(isSuffix = true, isWildcard = true)
        } else {
            currNode.isSuffix = true
        }
        if (exception) {
            currNode.isException = true
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
    private suspend fun findRegistrableDomain(hostname: String): String? =
        mutex.withLock {
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

        private val pslRuleRegex = Regex("^(!)?(\\*\\.)?([a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+$")

        /** Default URL to Mozilla-maintained Public Suffix List. */
        const val MOZILLA_PSL_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

        const val MOZILLA_PSL_RESOURCE_FILE = "${Constants.OFFLINE_DATA_PATH}/psl.txt"

        /**
         * Creates and initializes a [RegistrabilityChecker] using the given provider.
         *
         * @param domainsProvider source of PSL rules.
         * @param customRules a set of custom PSL rules to add to the main list.
         * @return an initialized [RegistrabilityChecker].
         */
        suspend fun create(
            domainsProvider: DomainsProvider,
            customRules: Set<String> = emptySet(),
        ): RegistrabilityChecker {
            logger.debug("Creating RegistrabilityChecker...")
            val index = RegistrabilityChecker(domainsProvider, customRules)
            index.refresh()
            logger.debug("RegistrabilityChecker created.")
            return index
        }
    }
}
