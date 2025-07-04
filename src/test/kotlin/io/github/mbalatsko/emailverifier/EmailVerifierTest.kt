package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.DnsLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailVerifierLocalTest {
    /**
     * Dummy DomainsProvider returning a fixed list.
     */
    private class FixedListProvider(
        private val items: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = items
    }

    /**
     * Fake DNS backend for integration: returns configured MX existence.
     */
    private class FakeDnsBackend(
        private val hasMx: Boolean,
    ) : DnsLookupBackend {
        override suspend fun hasMxRecords(hostname: String): Boolean = hasMx
    }

    private suspend fun buildVerifier(hasMx: Boolean): EmailVerifier =
        EmailVerifier(
            emailSyntaxChecker = EmailSyntaxChecker(),
            pslIndex = PslIndex.init(FixedListProvider(setOf("com", "co.uk"))),
            mxRecordChecker = MxRecordChecker(FakeDnsBackend(hasMx)),
            disposableEmailChecker =
                DisposableEmailChecker.init(
                    FixedListProvider(setOf("disposable.com")),
                ),
            gravatarChecker = null,
            freeChecker =
                FreeChecker.init(
                    FixedListProvider(setOf("free.com")),
                ),
            roleBasedUsernameChecker =
                RoleBasedUsernameChecker.init(
                    FixedListProvider(setOf("role")),
                ),
        )

    @Test
    fun `integration passes all checks`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("user@example.com")
            assertTrue(result.syntaxCheck == CheckResult.PASSED)
            assertTrue(result.registrabilityCheck == CheckResult.PASSED)
            assertTrue(result.mxRecordCheck == CheckResult.PASSED)
            assertTrue(result.disposabilityCheck == CheckResult.PASSED)
            assertTrue(result.freeCheck == CheckResult.PASSED)
            assertTrue(result.roleBasedUsernameCheck == CheckResult.PASSED)
            assertTrue(result.ok())
        }

    @Test
    fun `integration fails syntax`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("bad@@example.com")
            assertFalse(result.ok())
            assertTrue(result.syntaxCheck == CheckResult.FAILED)
            // other checks should be SKIPPED because syntax failed before them
            assertTrue(result.registrabilityCheck == CheckResult.SKIPPED)
            assertTrue(result.mxRecordCheck == CheckResult.SKIPPED)
            assertTrue(result.disposabilityCheck == CheckResult.SKIPPED)
            assertTrue(result.freeCheck == CheckResult.SKIPPED)
            assertTrue(result.roleBasedUsernameCheck == CheckResult.SKIPPED)
        }

    @Test
    fun `integration fails registrability`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("user@co.uk")
            assertFalse(result.ok())
            assertTrue(result.syntaxCheck == CheckResult.PASSED)
            assertTrue(result.registrabilityCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration fails mx record`() =
        runTest {
            val verifier = buildVerifier(hasMx = false)
            val result = verifier.verify("user@example.com")
            assertFalse(result.ok())
            assertTrue(result.syntaxCheck == CheckResult.PASSED)
            assertTrue(result.mxRecordCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration fails disposability`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("user@disposable.com")
            assertFalse(result.ok())
            assertTrue(result.disposabilityCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration passes, free fails`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("user@free.com")
            assertTrue(result.ok())
            assertTrue(result.freeCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration passes, role-based fails`() =
        runTest {
            val verifier = buildVerifier(hasMx = true)
            val result = verifier.verify("role@example.com")
            assertTrue(result.ok())
            assertTrue(result.roleBasedUsernameCheck == CheckResult.FAILED)
        }

    private class ErrorDnsBackend : DnsLookupBackend {
        override suspend fun hasMxRecords(hostname: String): Boolean = throw VerificationError("test error")
    }

    @Test
    fun `integration returns errored`() =
        runTest {
            val verifier =
                EmailVerifier(
                    emailSyntaxChecker = EmailSyntaxChecker(),
                    pslIndex = null,
                    mxRecordChecker = MxRecordChecker(ErrorDnsBackend()),
                    disposableEmailChecker = null,
                    gravatarChecker = null,
                    freeChecker = null,
                    roleBasedUsernameChecker = null,
                )
            val result = verifier.verify("user@example.com")
            assertTrue(result.mxRecordCheck == CheckResult.ERRORED)
        }

    @Test
    fun `integration skips disabled checks`() =
        runTest {
            val config =
                EmailVerifierConfig(
                    enableRegistrabilityCheck = false,
                    enableMxRecordCheck = false,
                    enableDisposabilityCheck = false,
                    enableFreeCheck = false,
                )
            val verifier = EmailVerifier.init(config)
            val result = verifier.verify("user@anydomain.test")
            // Syntax still runs
            assertTrue(result.syntaxCheck == CheckResult.PASSED)
            // All others skipped
            assertTrue(result.registrabilityCheck == CheckResult.SKIPPED)
            assertTrue(result.mxRecordCheck == CheckResult.SKIPPED)
            assertTrue(result.disposabilityCheck == CheckResult.SKIPPED)
            assertTrue(result.freeCheck == CheckResult.SKIPPED)
            assertTrue(result.ok())
        }
}

class EmailVerifierOnlineTest {
    val onlineEmailVerifier = runBlocking { EmailVerifier.init() }

    @Test
    fun `integration passes all checks online, but fails gravatar and free`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@gmail.com")
            assertTrue(result.ok())
            assertTrue(result.gravatarCheck == CheckResult.FAILED)
            assertTrue(result.freeCheck == CheckResult.FAILED)
            assertTrue(result.roleBasedUsernameCheck == CheckResult.PASSED)
        }

    @Test
    fun `integration passes free check`() =
        runTest {
            val result = onlineEmailVerifier.verify("maksym.balatsko@blindspot.ai")
            assertTrue(result.freeCheck == CheckResult.PASSED)
        }

    @Test
    fun `integration passes gravatar check`() =
        runTest {
            val result = onlineEmailVerifier.verify("jitewaboh@lagify.com")
            assertTrue(result.gravatarCheck == CheckResult.PASSED)
        }

    @Test
    fun `integration fails registrability`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@invalid.invalid")
            assertFalse(result.ok())
            assertTrue(result.registrabilityCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration fails mx record`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@somethingrandom.co.uk")
            assertFalse(result.ok())
            assertTrue(result.mxRecordCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration fails disposability`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@simplelogin.com")
            assertFalse(result.ok())
            assertTrue(result.disposabilityCheck == CheckResult.FAILED)
        }

    @Test
    fun `integration fails role-based`() =
        runTest {
            val result = onlineEmailVerifier.verify("admin@simplelogin.com")
            assertTrue(result.roleBasedUsernameCheck == CheckResult.FAILED)
        }
}
