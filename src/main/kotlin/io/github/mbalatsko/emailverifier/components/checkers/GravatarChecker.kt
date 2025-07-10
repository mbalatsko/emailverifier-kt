package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.VerificationError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import okio.ByteString.Companion.encodeUtf8

/**
 * Checks for the existence of a Gravatar associated with an email address.
 *
 * @property httpClient the HTTP client to use for requests.
 * @property baseURL the base URL for Gravatar avatar requests.
 */
class GravatarChecker(
    private val httpClient: HttpClient,
    private val baseURL: String = GRAVATAR_BASE_URL,
) {
    /**
     * Retrieves the Gravatar URL for a given email, if it exists.
     *
     * @param email the email address to check.
     * @return the Gravatar URL as a string, or null if no custom avatar is found.
     * @throws VerificationError if the request fails or the server returns an error.
     */
    suspend fun getGravatarUrl(email: String): String? {
        val emailHash = email.encodeUtf8().md5().hex()
        val url = "$baseURL/$emailHash?d=404"

        try {
            val resp = httpClient.get(url)
            if (resp.status.value >= 400 && resp.status.value != 404) {
                throw VerificationError("Gravatar server returned error: ${resp.status}")
            }
            return if (resp.status == HttpStatusCode.OK && resp.bodyAsText() != GRAVATAR_DEFAULT_MD5) {
                url
            } else {
                null
            }
        } catch (e: Exception) {
            throw VerificationError("Failed to connect to Gravatar", e)
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
