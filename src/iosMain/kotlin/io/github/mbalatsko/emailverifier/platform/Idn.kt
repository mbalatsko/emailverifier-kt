package io.github.mbalatsko.emailverifier.platform

import platform.Foundation.NSString
import platform.Foundation.stringByApplyingTransform

actual fun toASCII(input: String): String = (input as NSString).stringByApplyingTransform("IDNA; ToASCII;", false) ?: input

