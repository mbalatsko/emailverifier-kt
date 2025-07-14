package io.github.mbalatsko.emailverifier.components.core

import java.net.IDN

/**
 * Decomposed parts of a parsed email address.
 *
 * @property username the part before '+' and '@'.
 * @property plusTag the optional plus tag suffix (after '+', if present).
 * @property hostname the domain part of the email.
 */
data class EmailParts(
    val username: String,
    val plusTag: String,
    val hostname: String,
) {
    override fun toString(): String =
        if (plusTag != "") {
            "$username+$plusTag@$hostname"
        } else {
            "$username@$hostname"
        }

    /**
     * Returns the string representation of the email address without the plus-tag.
     */
    fun toStringNoPlus(): String = "$username@$hostname"
}

/**
 * Parses an email address into its logical parts.
 *
 * The local-part is split at the first '+' character (if present).
 * The domain is converted to ASCII form using IDNA (punycode).
 *
 * @param email the input email address.
 * @return the parsed [EmailParts].
 * @throws IllegalArgumentException if the email does not contain exactly one '@'.
 */
fun parseEmailParts(email: String): EmailParts {
    val emailParts = email.split('@')
    require(emailParts.size == 2) { "Email must have exactly one @ character." }

    val hostname = IDN.toASCII(emailParts[1]).lowercase()

    val usernameParts = emailParts[0].split('+', limit = 2)

    val username = usernameParts[0]
    val plusTag = usernameParts.getOrElse(1) { "" }
    return EmailParts(
        username,
        plusTag,
        hostname,
    )
}
