package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Base class for checkers that use a [DomainsProvider] to load a set of strings.
 *
 * @property domainsProvider the provider for the data.
 */
abstract class BaseChecker(
    private val domainsProvider: DomainsProvider,
) {
    /**
     * The set of data loaded from the [domainsProvider].
     */
    protected var dataSet: Set<String> = emptySet()

    /**
     * Loads the data from the [domainsProvider].
     */
    suspend fun loadData() {
        dataSet = domainsProvider.provide()
    }
}
