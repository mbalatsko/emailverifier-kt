package io.github.mbalatsko.emailverifier.components.checkers

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class MD5Test {
    val randomState = Random(42)

    fun md5Java(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testMD5Consistency() {
        for (i in 1..10_000) {
            val randomString =
                (1..255)
                    .map { randomState.nextInt(32, 127).toChar() } // printable ASCII
                    .joinToString("")
            val kotlinHash = MD5.hash(randomString)
            val javaHash = md5Java(randomString)
            assertTrue(kotlinHash == javaHash)
        }
    }
}
