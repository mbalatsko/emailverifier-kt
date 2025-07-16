package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.DnsLookupBackend
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.ktor.client.request.get
import org.slf4j.LoggerFactory

/**
 * Represents a DNS MX (Mail Exchange) record.
 * @property exchange the hostname of the mail server.
 * @property priority the preference value for this record (lower is more preferred).
 */
data class MxRecord(
    val exchange: String,
    val priority: Int,
)

/**
 * Data class holding the MX records found during the MX record check.
 * @property records A list of [MxRecord]s, or an empty list if none were found.
 */
data class MxRecordData(
    val records: List<MxRecord>,
)

/**
 * Component that checks MX record presence using a specified [DnsLookupBackend].
 *
 * @property dnsLookupBackend the backend used to perform DNS queries.
 */
class MxRecordChecker(
    private val dnsLookupBackend: DnsLookupBackend,
) : IChecker<MxRecordData, Unit> {
    /**
     * Retrieves MX records for the email's hostname using the configured [DnsLookupBackend].
     *
     * @param email the decomposed parts of the email address to check.
     * @param context (not used)
     * @return an [MxRecordData] object containing the list of found MX records.
     */
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): MxRecordData {
        logger.debug("Looking up MX records for hostname: {}", email.hostname)
        val records = dnsLookupBackend.getMxRecords(email.hostname)
        logger.debug("Found {} MX records for {}: {}", records.size, email.hostname, records)
        return MxRecordData(records)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MxRecordChecker::class.java)
    }
}
