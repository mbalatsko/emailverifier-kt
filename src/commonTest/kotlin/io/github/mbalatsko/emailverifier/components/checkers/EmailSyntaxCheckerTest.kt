package io.github.mbalatsko.emailverifier.components.checkers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailSyntaxCheckerTest {
    private val checker = EmailSyntaxChecker()

    @Test
    fun `valid dot-atom usernames`() {
        val validUsernames =
            listOf(
                "john.doe",
                "user123",
                "a",
                "AZaz09!#$%&'*+/=?^_`{|}~-",
            )
        validUsernames.forEach { assertTrue(checker.isUsernameValid(it), "Expected valid: $it") }
    }

    @Test
    fun `invalid dot-atom usernames`() {
        val invalidUsernames =
            listOf(
                "", // empty
                ".john", // leading dot
                "john.", // trailing dot
                "john..doe", // consecutive dots
                "john doe", // space
                "john@doe", // invalid char '@'
                "user()", // parens not allowed
            )
        invalidUsernames.forEach { assertFalse(checker.isUsernameValid(it), "Expected invalid: $it") }
    }

    @Test
    fun `valid quoted-string usernames`() {
        val validQuoted =
            listOf(
                "\"quoted\"",
                "\"with\\\"escape\"",
                "\"!#$%&'()*+-/=?^_`{|}~.@[]\"",
            )
        validQuoted.forEach { assertTrue(checker.isUsernameValid(it), "Expected valid quoted: $it") }
    }

    @Test
    fun `invalid quoted-string usernames`() {
        val invalidQuoted =
            listOf(
                "\"unclosed",
                "unopened\"",
                "\"bad\\\"escape", // escape at end
                "\"contains\r\n\"", // CR or LF inside
            )
        invalidQuoted.forEach { assertFalse(checker.isUsernameValid(it), "Expected invalid quoted: $it") }
    }

    @Test
    fun `valid plus-tags`() {
        val validTags =
            listOf(
                "",
                "tag",
                "123",
                "a.b-c_d~!#$%&'*+/=?^`{|}~",
            )
        validTags.forEach { assertTrue(checker.isPlusTagValid(it), "Expected valid plus-tag: $it") }
    }

    @Test
    fun `invalid plus-tags`() {
        val invalidTags =
            listOf(
                "has space",
                "has@char",
                "has,comma",
                "has;semi",
            )
        invalidTags.forEach { assertFalse(checker.isPlusTagValid(it), "Expected invalid plus-tag: $it") }
    }

    @Test
    fun `valid hostnames`() {
        val validHostnames =
            listOf(
                "example.com",
                "sub.domain.co.uk",
                "xn--bcher-kva.de",
                "localhost",
            )
        validHostnames.forEach { assertTrue(checker.isHostnameValid(it), "Expected valid hostname: $it") }
    }

    @Test
    fun `invalid hostnames`() {
        val invalidHostnames =
            listOf(
                "", // empty
                ".example.com", // leading dot
                "example.com.", // trailing dot
                "exa mple.com", // space
                "-startdash.com", // label starts with dash
                "enddash-.com", // label ends with dash
                "toolonglabel${"a".repeat(63)}.com", // label >63 chars
                "a".repeat(254), // hostname >253 chars
            )
        invalidHostnames.forEach { assertFalse(checker.isHostnameValid(it), "Expected invalid hostname: $it") }
    }
}
