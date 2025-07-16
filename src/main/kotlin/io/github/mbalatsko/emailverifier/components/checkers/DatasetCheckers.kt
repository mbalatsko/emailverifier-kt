package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import org.slf4j.LoggerFactory

/**
 * Enum representing the source of a match in a dataset check.
 */
enum class Source {
    ALLOW,
    DENY,
    DEFAULT,
}

/**
 * Data class holding the result of a dataset check.
 *
 * @property match true if a match was found in the dataset.
 * @property matchedOn the specific entry that was matched, or null if no match was found.
 * @property source the source of the match (e.g., "allow", "deny", "default").
 */
data class DatasetData(
    val match: Boolean,
    val matchedOn: String? = null,
    val source: Source? = null,
)

/**
 * Base class for checkers that verify if a part of an email address exists in a given dataset.
 *
 * @property domainsProvider the provider for the dataset.
 * @property allowSet a set of values to be treated as valid.
 * @property denySet a set of values to be treated as invalid.
 */
abstract class BaseInDatasetChecker(
    private val domainsProvider: DomainsProvider,
    protected val allowSet: Set<String>,
    protected val denySet: Set<String>,
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
        logger.debug("Loaded {} entries from {}", dataSet.size, domainsProvider::class.java.simpleName)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseInDatasetChecker::class.java)
    }
}

/**
 * A checker that verifies if an email's hostname is present in a dataset.
 * It checks the full hostname and its parent domains.
 */
class HostnameInDatasetChecker private constructor(
    domainsProvider: DomainsProvider,
    allowSet: Set<String>,
    denySet: Set<String>,
) : BaseInDatasetChecker(domainsProvider, allowSet, denySet) {
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

        val allowedCandidate = candidates.firstOrNull { it in allowSet }
        if (allowedCandidate != null) {
            return DatasetData(
                match = false,
                matchedOn = allowedCandidate,
                source = Source.ALLOW,
            )
        }

        val deniedCandidate = candidates.firstOrNull { it in denySet }
        if (deniedCandidate != null) {
            logger.trace("Hostname {} is in the deny set.", deniedCandidate)
            return DatasetData(
                match = true,
                matchedOn = deniedCandidate,
                source = Source.DENY,
            )
        }

        val matchedCandidate = candidates.firstOrNull { it in dataSet }
        logger.trace("Hostname matched in dataset: {}", matchedCandidate)
        return DatasetData(
            match = matchedCandidate != null,
            matchedOn = matchedCandidate,
            source = if (matchedCandidate != null) Source.DEFAULT else null,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HostnameInDatasetChecker::class.java)

        /**
         * Creates and initializes a [HostnameInDatasetChecker].
         *
         * @param domainsProvider the provider for the hostname dataset.
         * @param allowSet a set of hostnames to be treated as valid.
         * @param denySet a set of hostnames to be treated as invalid.
         * @return an initialized [HostnameInDatasetChecker].
         */
        suspend fun create(
            domainsProvider: DomainsProvider,
            allowSet: Set<String>,
            denySet: Set<String>,
        ): HostnameInDatasetChecker {
            logger.debug("Creating HostnameInDatasetChecker...")
            val hostnameInDatasetChecker = HostnameInDatasetChecker(domainsProvider, allowSet, denySet)
            hostnameInDatasetChecker.loadData()
            logger.debug("HostnameInDatasetChecker created.")
            return hostnameInDatasetChecker
        }
    }
}

/**
 * A checker that verifies if an email's username is present in a dataset.
 */
class UsernameInDatasetChecker private constructor(
    domainsProvider: DomainsProvider,
    allowSet: Set<String>,
    denySet: Set<String>,
) : BaseInDatasetChecker(domainsProvider, allowSet, denySet) {
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): DatasetData {
        if (email.username in allowSet) {
            logger.trace("Username {} is in the allow set.", email.username)
            return DatasetData(
                match = false,
                matchedOn = email.username,
                source = Source.ALLOW,
            )
        }

        if (email.username in denySet) {
            logger.trace("Username {} is in the deny set.", email.username)
            return DatasetData(
                match = true,
                matchedOn = email.username,
                source = Source.DENY,
            )
        }

        val match = email.username in dataSet
        logger.trace("Username matched in dataset: {}", match)
        return DatasetData(
            match = match,
            matchedOn = if (match) email.username else null,
            source = if (match) Source.DEFAULT else null,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UsernameInDatasetChecker::class.java)

        /**
         * Creates and initializes a [UsernameInDatasetChecker].
         *
         * @param domainsProvider the provider for the username dataset.
         * @param allowSet a set of usernames to be treated as valid.
         * @param denySet a set of usernames to be treated as invalid.
         * @return an initialized [UsernameInDatasetChecker].
         */
        suspend fun create(
            domainsProvider: DomainsProvider,
            allowSet: Set<String>,
            denySet: Set<String>,
        ): UsernameInDatasetChecker {
            logger.debug("Creating UsernameInDatasetChecker...")
            val usernameInDatasetChecker = UsernameInDatasetChecker(domainsProvider, allowSet, denySet)
            usernameInDatasetChecker.loadData()
            logger.debug("UsernameInDatasetChecker created.")
            return usernameInDatasetChecker
        }
    }
}
