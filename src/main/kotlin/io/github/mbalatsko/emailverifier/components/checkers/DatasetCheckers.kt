package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider

/**
 * Data class holding the result of a dataset check.
 *
 * @property match true if a match was found in the dataset.
 * @property matchedOn the specific entry that was matched, or null if no match was found.
 */
data class DatasetData(
    val match: Boolean,
    val matchedOn: String? = null,
)

/**
 * Base class for checkers that verify if a part of an email address exists in a given dataset.
 *
 * @property domainsProvider the provider for the dataset.
 */
abstract class BaseInDatasetChecker(
    private val domainsProvider: DomainsProvider,
) : IChecker<DatasetData, Unit> {
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

/**
 * A checker that verifies if an email's hostname is present in a dataset.
 * It checks the full hostname and its parent domains.
 */
class HostnameInDatasetChecker private constructor(
    domainsProvider: DomainsProvider,
) : BaseInDatasetChecker(domainsProvider) {
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): DatasetData {
        val labels = email.hostname.split('.')

        val candidates = mutableListOf<String>()
        // Check all subdomains of given hostname up to level 2 (e.g. google.com)
        for (i in 0..labels.size - 2) {
            val partialHostname = labels.slice(i..labels.size - 1).joinToString(".")
            candidates.add(partialHostname)
        }
        val matchedCandidate = candidates.firstOrNull { it in dataSet }
        return DatasetData(
            match = matchedCandidate != null,
            matchedOn = matchedCandidate,
        )
    }

    companion object {
        /**
         * Creates and initializes a [HostnameInDatasetChecker].
         *
         * @param domainsProvider the provider for the hostname dataset.
         * @return an initialized [HostnameInDatasetChecker].
         */
        suspend fun create(domainsProvider: DomainsProvider): HostnameInDatasetChecker {
            val hostnameInDatasetChecker = HostnameInDatasetChecker(domainsProvider)
            hostnameInDatasetChecker.loadData()
            return hostnameInDatasetChecker
        }
    }
}

/**
 * A checker that verifies if an email's username is present in a dataset.
 */
class UsernameInDatasetChecker private constructor(
    domainsProvider: DomainsProvider,
) : BaseInDatasetChecker(domainsProvider) {
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): DatasetData =
        DatasetData(
            match = email.username in dataSet,
            matchedOn = if (email.username in dataSet) email.username else null,
        )

    companion object {
        /**
         * Creates and initializes a [UsernameInDatasetChecker].
         *
         * @param domainsProvider the provider for the username dataset.
         * @return an initialized [UsernameInDatasetChecker].
         */
        suspend fun create(domainsProvider: DomainsProvider): UsernameInDatasetChecker {
            val usernameInDatasetChecker = UsernameInDatasetChecker(domainsProvider)
            usernameInDatasetChecker.loadData()
            return usernameInDatasetChecker
        }
    }
}
