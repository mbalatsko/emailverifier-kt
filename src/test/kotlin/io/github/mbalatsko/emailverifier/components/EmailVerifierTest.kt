package io.github.mbalatsko.emailverifier.components

import io.github.mbalatsko.emailverifier.CheckResult
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import io.github.mbalatsko.emailverifier.EmailVerifier
import io.github.mbalatsko.emailverifier.EmailVerifierConfig
import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.DnsLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import kotlinx.coroutines.runBlocking

/**
 * Dummy DomainsProvider returning a fixed list.
 */
class FixedListProvider(private val items: List<String>) : DomainsProvider {
    override suspend fun provide(): List<String> = items
}

/**
 * Fake DNS backend for integration: returns configured MX existence.
 */
class FakeDnsBackend(private val hasMx: Boolean) : DnsLookupBackend {
    override suspend fun hasMxRecords(hostname: String): Boolean = hasMx
}

class EmailVerifierLocalTest {

    private suspend fun buildVerifier(
        hasMx: Boolean,
        disposable: Boolean
    ): EmailVerifier {
        val psl = PslIndex.init(FixedListProvider(listOf("com", "co.uk")))
        val disposableChecker = DisposableEmailChecker.init(FixedListProvider(
            if (disposable) listOf("disposable.com") else emptyList()
        ))
        val mxChecker = MxRecordChecker(FakeDnsBackend(hasMx))
        return EmailVerifier(
            emailSyntaxChecker = EmailSyntaxChecker(),
            pslIndex = psl,
            mxRecordChecker = mxChecker,
            disposableEmailChecker = disposableChecker
        )
    }

    @Test
    fun `integration passes all checks`() = runTest {
        val verifier = buildVerifier(hasMx = true, disposable = false)
        val result = verifier.verify("user@example.com")
        assertTrue(result.syntaxCheck == CheckResult.PASSED)
        assertTrue(result.registrabilityCheck == CheckResult.PASSED)
        assertTrue(result.mxRecordCheck == CheckResult.PASSED)
        assertTrue(result.disposabilityCheck == CheckResult.PASSED)
        assertTrue(result.ok())
    }

    @Test
    fun `integration fails syntax`() = runTest {
        val verifier = buildVerifier(hasMx = true, disposable = false)
        val result = verifier.verify("bad@@example.com")
        assertFalse(result.ok())
        assertTrue(result.syntaxCheck == CheckResult.FAILED)
        // other checks should be SKIPPED because syntax failed before them
        assertTrue(result.registrabilityCheck == CheckResult.SKIPPED)
        assertTrue(result.mxRecordCheck == CheckResult.SKIPPED)
        assertTrue(result.disposabilityCheck == CheckResult.SKIPPED)
    }

    @Test
    fun `integration fails registrability`() = runTest {
        val verifier = buildVerifier(hasMx = true, disposable = false)
        val result = verifier.verify("user@co.uk")
        assertFalse(result.ok())
        assertTrue(result.syntaxCheck == CheckResult.PASSED)
        assertTrue(result.registrabilityCheck == CheckResult.FAILED)
    }

    @Test
    fun `integration fails mx record`() = runTest {
        val verifier = buildVerifier(hasMx = false, disposable = false)
        val result = verifier.verify("user@example.com")
        assertFalse(result.ok())
        assertTrue(result.syntaxCheck == CheckResult.PASSED)
        assertTrue(result.mxRecordCheck == CheckResult.FAILED)
    }

    @Test
    fun `integration fails disposability`() = runTest {
        val verifier = buildVerifier(hasMx = true, disposable = true)
        val result = verifier.verify("user@disposable.com")
        assertFalse(result.ok())
        assertTrue(result.disposabilityCheck == CheckResult.FAILED)
    }

    @Test
    fun `integration skips disabled checks`() = runTest {
        val config = EmailVerifierConfig(
            enableRegistrabilityCheck = false,
            enableMxRecordCheck = false,
            enableDisposabilityCheck = false
        )
        val verifier = EmailVerifier.init(config)
        val result = verifier.verify("user@anydomain.test")
        // Syntax still runs
        assertTrue(result.syntaxCheck == CheckResult.PASSED)
        // All others skipped
        assertTrue(result.registrabilityCheck == CheckResult.SKIPPED)
        assertTrue(result.mxRecordCheck == CheckResult.SKIPPED)
        assertTrue(result.disposabilityCheck == CheckResult.SKIPPED)
        assertTrue(result.ok())
    }
}

class EmailVerifierOnlineTest {
    val onlineEmailVerifier = runBlocking { EmailVerifier.init() }

    @Test
    fun `integration passes all checks online`() = runTest {
        val result = onlineEmailVerifier.verify("mbalatsko@gmail.com")
        assertTrue(result.ok())
    }

    @Test
    fun `integration fails registrability online`() = runTest {
        val result = onlineEmailVerifier.verify("mbalatsko@invalid.invalid")
        assertFalse(result.ok())
        assertTrue(result.registrabilityCheck == CheckResult.FAILED)
    }

    @Test
    fun `integration fails mx record online`() = runTest {
        val result = onlineEmailVerifier.verify("mbalatsko@somethingrandom.co.uk")
        assertFalse(result.ok())
        assertTrue(result.mxRecordCheck == CheckResult.FAILED)
    }

    @Test
    fun `integration fails disposability online`() = runTest {
        val result = onlineEmailVerifier.verify("mbalatsko@simplelogin.com")
        assertFalse(result.ok())
        assertTrue(result.disposabilityCheck == CheckResult.FAILED)
    }
}
