package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts

/**
 * Data class holding the validity of each part of the email syntax.
 * @property username true if the username part is valid.
 * @property plusTag true if the plus-tag part is valid.
 * @property hostname true if the hostname part is valid.
 */
data class SyntaxValidationData(
    val username: Boolean,
    val plusTag: Boolean,
    val hostname: Boolean,
)

/**
 * Performs syntax validation on components of an email address.
 *
 * Validates username (local-part), optional plus-tag extensions, and domain hostname
 * according to a subset of RFC 5322 and IDN rules.
 */
class EmailSyntaxChecker : IChecker<SyntaxValidationData, Unit> {
    override suspend fun check(
        email: EmailParts,
        context: Unit,
    ): SyntaxValidationData =
        SyntaxValidationData(
            username = isUsernameValid(email.username),
            plusTag = isPlusTagValid(email.plusTag),
            hostname = isHostnameValid(email.hostname),
        )

    /**
     * Checks whether the provided username (local-part of email) is syntactically valid.
     *
     * Supports both quoted-string and dot-atom formats as specified in RFC 5322.
     *
     * @param username the local-part of the email address.
     * @return `true` if valid, `false` otherwise.
     */
    fun isUsernameValid(username: String): Boolean {
        // 1. Length check
        if (username.isEmpty() || username.length > 64) return false

        // 2. Check for quoted-string
        if (username.startsWith("\"") && username.endsWith("\"")) {
            // Allow quoted-pair (\x) and valid quoted characters per RFC 5322
            val inner = username.substring(1, username.length - 1)
            // Quoted string: any ASCII except CR, LF, or unescaped quote/backslash
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\') {
                    // Escaped char: must be followed by another char
                    if (i + 1 >= inner.length) return false
                    i += 2
                    continue
                }
                if (c == '"' || c == '\r' || c == '\n') return false
                i++
            }
            return true
        }

        // 3. Dot-atom text: atext *( "." atext )
        if (!dotAtomRegex.matches(username)) return false
        // No leading, trailing, or consecutive dots
        if (username.startsWith(".") || username.endsWith(".") || username.contains("..")) return false
        return true
    }

    /**
     * Validates the syntax of a plus-tag suffix (e.g., in user+tag@example.com).
     *
     * Allows characters permitted in dot-atom format.
     *
     * @param plusTag the plus-tag suffix without the '+' sign.
     * @return `true` if empty or valid, `false` otherwise.
     */
    fun isPlusTagValid(plusTag: String): Boolean = plusTag.isEmpty() || plusTagRegex.matches(plusTag)

    /**
     * Validates the syntax of a hostname, following rules from RFC 1035 and RFC 5890.
     *
     * Ensures each label is within valid length, uses allowed characters, and structure
     * complies with domain name constraints.
     *
     * @param hostname the domain part of an email address.
     * @return `true` if syntactically valid, `false` otherwise.
     */
    fun isHostnameValid(hostname: String): Boolean {
        // 1. Length check
        if (hostname.length > 253) return false

        // 2. No leading/trailing dot
        if (hostname.startsWith(".") || hostname.endsWith(".")) return false

        // 3. Validate each label
        val labels = hostname.split('.')

        for (label in labels) {
            if (label.length !in 1..63) return false
            if (label.startsWith("-") || label.endsWith("-")) return false

            // Allow Unicode letters, digits, and hyphen (RFC 5890 for IDN, RFC 1035 for ASCII)
            if (!label.matches(hostnameRegex)) return false
        }
        return true
    }

    companion object {
        // atext *( "." atext )
        // atext = ALPHA / DIGIT /    ; Any character except specials, space and control
        //         "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "/" / "=" / "?" /
        //         "^" / "_" / "`" / "{" / "|" / "}" / "~"
        private const val ATEXT = "[a-zA-Z0-9!#\$%&'*+/=?^_`{|}~-]"
        private val dotAtomRegex = Regex("^$ATEXT+(\\.$ATEXT+)*\$")
        private val hostnameRegex = Regex("^[a-zA-Z0-9-]+$")

        /**
         * Regular expression for validating plus-tag syntax.
         */
        private val plusTagRegex = Regex("^[A-Za-z0-9!#\$%&'*+/=?^_`{|}~.-]+$")
    }
}
