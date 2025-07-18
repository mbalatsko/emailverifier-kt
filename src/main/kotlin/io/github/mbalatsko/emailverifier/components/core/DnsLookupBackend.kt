package io.github.mbalatsko.emailverifier.components.core

import io.github.mbalatsko.emailverifier.components.checkers.MxRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Interface for DNS MX record lookup backends.
 */
interface DnsLookupBackend {
    /**
     * Retrieves the MX records for a given hostname.
     *
     * @param hostname the domain to query.
     * @return a list of [MxRecord]s, sorted by priority.
     */
    suspend fun getMxRecords(hostname: String): List<MxRecord>
}

/**
 * Implementation of [DnsLookupBackend] using Google's DNS-over-HTTPS (DoH) API.
 */
class GoogleDoHLookupBackend(
    private val httpClient: HttpClient,
    private val baseURL: String = GOOGLE_DOH_URL,
) : DnsLookupBackend {
    @Serializable
    private data class Answer(
        val data: String,
        val name: String,
        val type: Int,
    )

    @Serializable
    private data class DnsResponse(
        val Answer: List<Answer>? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Queries the DoH endpoint for MX records and parses them.
     *
     * @param hostname the domain to query.
     * @return a list of [MxRecord]s sorted by priority.
     * @throws ConnectionError if the request fails or the server returns an error.
     */
    override suspend fun getMxRecords(hostname: String): List<MxRecord> {
        val url = "$baseURL?name=$hostname&type=MX"
        logger.debug("Querying DoH server for MX records: {}", url)
        try {
            val resp = httpClient.get(url)
            if (resp.status.value >= 400) {
                logger.warn("DoH server returned error: {}", resp.status)
                throw ConnectionError("DoH server returned error: ${resp.status}")
            }
            val raw = resp.bodyAsText()
            logger.trace("DoH server response: {}", raw)
            val dnsResponse = json.decodeFromString<DnsResponse>(raw)

            val records =
                dnsResponse.Answer
                    ?.filter { it.type == MX_TYPE }
                    ?.mapNotNull {
                        val parts = it.data.split(" ")
                        if (parts.size == 2) {
                            MxRecord(exchange = parts[1].removeSuffix("."), priority = parts[0].toInt())
                        } else {
                            logger.warn("Invalid MX record format: {}", it.data)
                            null
                        }
                    }?.sortedBy { -it.priority } ?: emptyList()
            logger.debug("Parsed {} MX records for {}.", records.size, hostname)
            return records
        } catch (e: Exception) {
            logger.error("Failed to connect to DoH server at {}", baseURL, e)
            throw ConnectionError("Failed to connect to DoH server", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleDoHLookupBackend::class.java)

        /** Type code for MX entries **/
        private const val MX_TYPE = 15

        /** Default URL for Google's DNS-over-HTTPS resolver. */
        const val GOOGLE_DOH_URL = "https://dns.google/resolve"
    }
}
