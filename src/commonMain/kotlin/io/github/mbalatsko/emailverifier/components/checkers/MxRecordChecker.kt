package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.VerificationError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Interface for DNS MX record lookup backends.
 */
interface DnsLookupBackend {
    /**
     * Checks if the given hostname has one or more MX records.
     *
     * @param hostname the domain to query.
     * @return `true` if MX records are present, `false` otherwise.
     */
    suspend fun hasMxRecords(hostname: String): Boolean
}

/**
 * Implementation of [DnsLookupBackend] using Google's DNS-over-HTTPS (DoH) API.
 *
 * Issues HTTP GET requests to a URL of the form:
 * `[baseURL]?name=<hostname>&type=MX`
 *
 * Expects a JSON response containing an optional `Answer` array.
 * Each element of `Answer` must include at minimum: `name` (String), `type` (Int), and `data` (String).
 * A non-empty `Answer` array with type 15 entries (MX) indicates presence of MX records.
 *
 * @property baseURL the base URL for the DoH endpoint (defaults to Google's resolver).
 * @property httpClient HTTP client used to get Google-like DoH response from [baseURL]?name=<hostname>&type=MX
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
     * Queries the DoH endpoint for MX records associated with the given hostname.
     *
     * @param hostname the domain to query.
     * @return `true` if at least one MX record is found, `false` otherwise.
     * @throws VerificationError if the request fails or the server returns a 5xx error.
     */
    override suspend fun hasMxRecords(hostname: String): Boolean {
        val url = "$baseURL?name=$hostname&type=MX"
        try {
            val resp = httpClient.get(url)
            if (resp.status.value >= 500) {
                throw VerificationError("DoH server returned error: ${resp.status}")
            }
            val raw = resp.bodyAsText()
            val dnsResponse = json.decodeFromString<DnsResponse>(raw)
            return dnsResponse.Answer?.any { it.type == MX_TYPE } == true
        } catch (e: Exception) {
            throw VerificationError("Failed to connect to DoH server", e)
        }
    }

    companion object {
        /** Type code for MX entries **/
        private const val MX_TYPE = 15

        /** Default URL for Google's DNS-over-HTTPS resolver. */
        const val GOOGLE_DOH_URL = "https://dns.google/resolve"
    }
}

/**
 * Component that checks MX record presence using a specified [DnsLookupBackend].
 *
 * @property dnsLookupBackend the backend used to perform DNS queries.
 */
class MxRecordChecker(
    val dnsLookupBackend: DnsLookupBackend,
) {
    /**
     * Determines whether MX records exist for the given hostname.
     *
     * @param hostname the domain to check.
     * @return `true` if MX records are present, `false` otherwise.
     */
    suspend fun isPresent(hostname: String): Boolean = dnsLookupBackend.hasMxRecords(hostname)
}
