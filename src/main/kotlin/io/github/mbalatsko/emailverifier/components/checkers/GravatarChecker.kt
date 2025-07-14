package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.github.mbalatsko.emailverifier.components.core.EmailParts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import okio.ByteString.Companion.encodeUtf8

/**
 * Data class holding the Gravatar URL found during the Gravatar check.
 * @property gravatarUrl The Gravatar URL string, or null if no custom avatar was found.
 */
data class GravatarData(
    val gravatarUrl: String?,
)

/**
 * Checks for the existence of a Gravatar associated with an email address.
 *
 * @property httpClient the HTTP client to use for requests.
 * @property baseURL the base URL for Gravatar avatar requests.
 */
class GravatarChecker(
    private val httpClient: HttpClient,
    private val baseURL: String = GRAVATAR_BASE_URL,
) : IChecker<GravatarData, Unit> {
    /**
     * Checks for a Gravatar by hashing the email and querying the Gravatar service.
     *
     * @param email the decomposed parts of the email address to check.
     * @param context (not used)
     * @return a [GravatarData] object containing the Gravatar URL if found.
     * @throws ConnectionError if the request fails or the server returns an error.
     */
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): GravatarData {
        val emailHash =
            email
                .toStringNoPlus()
                .encodeUtf8()
                .md5()
                .hex()
        val url = "$baseURL/$emailHash?d=404"

        try {
            val resp = httpClient.get(url)
            if (resp.status.value >= 400 && resp.status.value != 404) {
                throw ConnectionError("Gravatar server returned error: ${resp.status}")
            }
            return if (resp.status == HttpStatusCode.OK && resp.bodyAsText() != GRAVATAR_DEFAULT_MD5) {
                GravatarData(url)
            } else {
                GravatarData(null)
            }
        } catch (e: Exception) {
            throw ConnectionError("Failed to connect to Gravatar", e)
        }
    }

    companion object {
        /**
         * Default Gravatar avatar URL.
         */
        const val GRAVATAR_BASE_URL = "https://www.gravatar.com/avatar"

        /**
         * MD5 hash returned by Gravatar when no custom avatar is set.
         */
        const val GRAVATAR_DEFAULT_MD5 = "d5fe5cbcc31cff5f8ac010db72eb000c"
    }
}
