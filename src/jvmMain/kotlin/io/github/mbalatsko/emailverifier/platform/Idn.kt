package io.github.mbalatsko.emailverifier.platform

import java.net.IDN

actual fun toASCII(input: String): String = IDN.toASCII(input)
