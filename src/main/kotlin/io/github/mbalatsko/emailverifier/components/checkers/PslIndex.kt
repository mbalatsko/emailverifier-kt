package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Public Suffix List (PSL) index for determining registrability of domain names.
 *
 * Parses rules from a [DomainsProvider], typically using the PSL format from publicsuffix.org.
 * Supports normal, wildcard (`*.`), and exception (`!`) rules per PSL specification.
 */
class PslIndex(val domainsProvider: DomainsProvider) {
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
        var isWildcard: Boolean = false
    )

    private var root = Node()

    /**
     * Rebuilds the PSL index by fetching and parsing rules from [domainsProvider].
     * Must be called before using [isHostnameRegistrable].
     */
    suspend fun build() {
        root = Node()
        val rules = domainsProvider.provide()
        rules.forEach { add(it) }
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
     * Determines whether the given hostname is registrable.
     *
     * A hostname is registrable if it has more labels than the matching PSL suffix.
     * For example, `example.co.uk` is registrable, but `co.uk` is not.
     *
     * @param hostname the domain name to check.
     * @return `true` if registrable, `false` otherwise.
     */
    fun isHostnameRegistrable(hostname: String): Boolean {
        val labels = hostname.trim().lowercase().split(".").reversed()
        // TLDs are not registrable in general
        if (labels.size == 1) {
            return false
        }
        val matchLen = findMatchingRule(labels)
        return matchLen != null && labels.size > matchLen
    }

    /**
     * Computes the length (in labels) of the best matching PSL rule.
     *
     * Applies standard PSL matching semantics: longest match, wildcard support, and exception handling.
     *
     * @param labels reversed domain labels (TLD first).
     * @return length of the matching rule in labels, or `null` if no match found.
     */
    private fun findMatchingRule(labels: List<String>): Int? {
        var node = root
        var matchLen: Int? = null
        var exceptionMatchLen: Int? = null
        for ((i, label) in labels.withIndex()) {
            val next = node.children[label] ?: node.children["*"]
            if (next == null) break
            node = next
            if (node.isException) {
                exceptionMatchLen = i
            } else if (node.isSuffix || node.isWildcard) {
                matchLen = i + 1
            }
        }
        return exceptionMatchLen ?: matchLen
    }

    companion object {
        /** Default URL to Mozilla-maintained Public Suffix List. */
        const val MOZILLA_PSL_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

        /**
         * Constructs and initializes a [PslIndex] using the given provider.
         *
         * @param domainsProvider source of PSL rules.
         * @return an initialized [PslIndex].
         */
        suspend fun init(domainsProvider: DomainsProvider): PslIndex {
            val index = PslIndex(domainsProvider)
            index.build()
            return index
        }
    }
}