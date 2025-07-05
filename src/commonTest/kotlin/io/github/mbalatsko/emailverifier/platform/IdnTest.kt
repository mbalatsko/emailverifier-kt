package io.github.mbalatsko.emailverifier.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class IdnTest {

    @Test
    fun testToASCII_punycodeConversion() {
        val unicodeDomain = "bücher.example"
        val expectedPunycode = "xn--bcher-kva.example"
        assertEquals(expectedPunycode, toASCII(unicodeDomain))
    }
}
