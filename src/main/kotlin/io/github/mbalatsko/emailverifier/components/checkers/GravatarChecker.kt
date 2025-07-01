package io.github.mbalatsko.emailverifier.components.checkers

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

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
        val emailHash = MD5.hash(email)
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

/**
 * MD5 hash computation utility.
 *
 * Implements the MD5 hashing algorithm in Pure Kotlin
 */
object MD5 {
    private val r =
        intArrayOf(
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
        )

    private val k =
        IntArray(64) { i ->
            ((1L shl 32) * kotlin.math.abs(kotlin.math.sin(i + 1.0))).toLong().toInt()
        }

    /**
     * Computes the MD5 digest for the given input bytes.
     *
     * @param input the byte array to hash.
     * @return a 16-byte MD5 digest.
     */
    fun digest(input: ByteArray): ByteArray {
        val message = input + byteArrayOf(0x80.toByte())
        var newLength = message.size
        while ((newLength % 64) != 56) newLength++
        val padded = ByteArray(newLength + 8)
        message.copyInto(padded)
        val bitsLen = (input.size * 8).toLong()
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = (bitsLen ushr (8 * i) and 0xFF).toByte()
        }

        // *** Correct initial values ***
        var a = 0x67452301
        var b = 0xefcdab89.toInt()
        var c = 0x98badcfe.toInt()
        var d = 0x10325476

        fun leftRotate(
            x: Int,
            c: Int,
        ) = (x shl c) or (x ushr (32 - c))

        for (i in padded.indices step 64) {
            val chunk = padded.copyOfRange(i, i + 64)
            val m = IntArray(16)
            for (j in 0 until 16) {
                m[j] = ((chunk[j * 4].toInt() and 0xFF)) or
                    ((chunk[j * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((chunk[j * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((chunk[j * 4 + 3].toInt() and 0xFF) shl 24)
            }

            var aa = a
            var bb = b
            var cc = c
            var dd = d

            for (j in 0 until 64) {
                var f = 0
                var g = 0
                when (j) {
                    in 0..15 -> {
                        f = (bb and cc) or (bb.inv() and dd)
                        g = j
                    }
                    in 16..31 -> {
                        f = (dd and bb) or (dd.inv() and cc)
                        g = (5 * j + 1) % 16
                    }
                    in 32..47 -> {
                        f = bb xor cc xor dd
                        g = (3 * j + 5) % 16
                    }
                    else -> {
                        f = cc xor (bb or dd.inv())
                        g = (7 * j) % 16
                    }
                }
                val temp = dd
                dd = cc
                cc = bb
                bb = bb + leftRotate(aa + f + k[j] + m[g], r[j])
                aa = temp
            }

            a += aa
            b += bb
            c += cc
            d += dd
        }

        val out = ByteArray(16)
        for ((i, n) in listOf(a, b, c, d).withIndex()) {
            out[i * 4] = (n and 0xFF).toByte()
            out[i * 4 + 1] = (n ushr 8 and 0xFF).toByte()
            out[i * 4 + 2] = (n ushr 16 and 0xFF).toByte()
            out[i * 4 + 3] = (n ushr 24 and 0xFF).toByte()
        }
        return out
    }

    /**
     * Computes the MD5 hash of the given text and returns it as a lowercase hex string.
     *
     * @param text the input string to hash.
     * @return the MD5 hash in hexadecimal form.
     */
    fun hash(text: String): String =
        digest(text.encodeToByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
