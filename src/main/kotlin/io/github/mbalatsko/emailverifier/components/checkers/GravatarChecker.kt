package io.github.mbalatsko.emailverifier.components.checkers

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import okio.ByteString.Companion.encodeUtf8

/**
 * Checks for the existence of a Gravatar associated with an email address.
 *
 * Sends an HTTP GET request to the Gravatar service for the MD5 hash of the email
 * and returns true if a custom avatar exists (i.e., the response is 200 OK and
 * the returned hash does not match the default Gravatar hash).
 *
 * @property baseURL the base URL for Gravatar avatar requests (defaults to [GRAVATAR_BASE_URL]}).
 * @property httpClient the HTTP client to use for requests (defaults to CIO engine).
 */
class GravatarChecker(
    val baseURL: String = GRAVATAR_BASE_URL,
    val httpClient: HttpClient = HttpClient(CIO),
) {
    /**
     * Determines whether a Gravatar exists for the given email.
     *
     * @param email the email address to check (case-insensitive).
     * @return `true` if a custom Gravatar is available, `false` otherwise.
     */
    suspend fun hasGravatar(email: String): Boolean {
        val emailHash = email.encodeUtf8().md5().hex()
        val resp = httpClient.get("$baseURL/$emailHash?d=404")
        val respHash = resp.bodyAsText()
        return resp.status == HttpStatusCode.OK && respHash != GRAVATAR_DEFAULT_MD5
    }

    companion object {
        /**
         * Default Gravatar avatar URL.
         */
        val GRAVATAR_BASE_URL = "https://www.gravatar.com/avatar"

        /**
         * MD5 hash returned by Gravatar when no custom avatar is set.
         */
        val GRAVATAR_DEFAULT_MD5 = "d5fe5cbcc31cff5f8ac010db72eb000c"
    }
}
