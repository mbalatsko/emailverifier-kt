package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.DnsLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecord
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
    private class FixedListProvider(
        private val items: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = items
    }

    private class FakeDnsBackend(
        private val mxRecords: List<MxRecord>,
    ) : DnsLookupBackend {
        override suspend fun getMxRecords(hostname: String): List<MxRecord> = mxRecords
    }

    private suspend fun buildVerifier(mxRecords: List<MxRecord>): EmailVerifier =
        EmailVerifier(
            emailSyntaxChecker = EmailSyntaxChecker(),
            pslIndex = PslIndex.init(FixedListProvider(setOf("com", "co.uk"))),
            mxRecordChecker = MxRecordChecker(FakeDnsBackend(mxRecords)),
            disposableEmailChecker = DisposableEmailChecker.init(FixedListProvider(setOf("disposable.com"))),
            gravatarChecker = null,
            freeChecker = FreeChecker.init(FixedListProvider(setOf("free.com"))),
            roleBasedUsernameChecker = RoleBasedUsernameChecker.init(FixedListProvider(setOf("role"))),
        )

    @Test
    fun `isLikelyDeliverable returns true when all checks pass`() =
        runTest {
            val verifier = buildVerifier(mxRecords = listOf(MxRecord("mx.example.com", 10)))
            val result = verifier.verify("user@example.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.syntax is CheckResult.Passed)
            assertTrue(result.registrability is CheckResult.Passed)
            assertTrue(result.mx is CheckResult.Passed)
            assertTrue(result.disposable is CheckResult.Passed)
            assertTrue(result.free is CheckResult.Passed)
            assertTrue(result.roleBasedUsername is CheckResult.Passed)
        }

    @Test
    fun `isLikelyDeliverable returns false when syntax fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = emptyList())
            val result = verifier.verify("bad@@example.com")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.syntax is CheckResult.Failed)
            assertTrue(result.registrability is CheckResult.Skipped)
            assertTrue(result.mx is CheckResult.Skipped)
        }

    @Test
    fun `isLikelyDeliverable returns false when registrability fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = listOf(MxRecord("mx.co.uk", 10)))
            val result = verifier.verify("user@co.uk")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.registrability is CheckResult.Failed)
        }

    @Test
    fun `isLikelyDeliverable returns false when mx record check fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = emptyList())
            val result = verifier.verify("user@example.com")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.mx is CheckResult.Failed)
        }

    @Test
    fun `isLikelyDeliverable returns false when disposability fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = listOf(MxRecord("mx.disposable.com", 10)))
            val result = verifier.verify("user@disposable.com")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.disposable is CheckResult.Failed)
        }

    @Test
    fun `isLikelyDeliverable is true when free check fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = listOf(MxRecord("mx.free.com", 10)))
            val result = verifier.verify("user@free.com")
            assertTrue(result.isLikelyDeliverable()) // free is not a strong indicator
            assertTrue(result.free is CheckResult.Failed)
        }

    @Test
    fun `isLikelyDeliverable is true when role-based check fails`() =
        runTest {
            val verifier = buildVerifier(mxRecords = listOf(MxRecord("mx.example.com", 10)))
            val result = verifier.verify("role@example.com")
            assertTrue(result.isLikelyDeliverable()) // role-based is not a strong indicator
            assertTrue(result.roleBasedUsername is CheckResult.Failed)
        }

    private class ErrorDnsBackend : DnsLookupBackend {
        override suspend fun getMxRecords(hostname: String): List<MxRecord> = throw VerificationError("test error")
    }

    @Test
    fun `check returns errored on backend error`() =
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
            assertTrue(result.mx is CheckResult.Errored)
        }

    @Test
    fun `disabled checks are skipped`() =
        runTest {
            val verifier =
                emailVerifier {
                    registrability { enabled = false }
                    mxRecord { enabled = false }
                    disposability { enabled = false }
                    free { enabled = false }
                    gravatar { enabled = false }
                    roleBasedUsername { enabled = false }
                }
            val result = verifier.verify("user@anydomain.test")
            assertTrue(result.syntax is CheckResult.Passed)
            assertTrue(result.registrability is CheckResult.Skipped)
            assertTrue(result.mx is CheckResult.Skipped)
            assertTrue(result.disposable is CheckResult.Skipped)
            assertTrue(result.free is CheckResult.Skipped)
            assertTrue(result.isLikelyDeliverable())
        }
}

class EmailVerifierOnlineTest {
    private val onlineEmailVerifier = runBlocking { emailVerifier {} }

    @Test
    fun `gmail passes main checks but fails free and gravatar`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@gmail.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.gravatar is CheckResult.Failed<*>)
            assertTrue(result.free is CheckResult.Failed<*>)
            assertTrue(result.roleBasedUsername is CheckResult.Passed<*>)
        }

    @Test
    fun `corporate email passes free check`() =
        runTest {
            val result = onlineEmailVerifier.verify("maksym.balatsko@blindspot.ai")
            assertTrue(result.free is CheckResult.Passed)
        }

    @Test
    fun `email with gravatar passes gravatar check`() =
        runTest {
            val result = onlineEmailVerifier.verify("jitewaboh@lagify.com")
            assertTrue(result.gravatar is CheckResult.Passed)
        }

    @Test
    fun `invalid domain fails registrability`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@invalid.invalid")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.registrability is CheckResult.Failed)
        }

    @Test
    fun `domain with no mx records fails mx check`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@somethingrandom.co.uk")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.mx is CheckResult.Failed)
        }

    @Test
    fun `disposable domain fails disposability check`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@simplelogin.com")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.disposable is CheckResult.Failed)
        }

    @Test
    fun `role-based username fails its check`() =
        runTest {
            val result = onlineEmailVerifier.verify("admin@simplelogin.com")
            assertTrue(result.roleBasedUsername is CheckResult.Failed)
        }
}

class EmailVerifierOfflineTest {
    private val offlineEmailVerifier = runBlocking { emailVerifier { allOffline = true } }

    @Test
    fun `offline mode works and skips online checks`() =
        runTest {
            val result = offlineEmailVerifier.verify("mbalatsko@gmail.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.free is CheckResult.Failed)
            assertTrue(result.roleBasedUsername is CheckResult.Passed)

            assertTrue(result.gravatar is CheckResult.Skipped)
            assertTrue(result.mx is CheckResult.Skipped)
        }

    @Test
    fun `offline free check passes for non-free domain`() =
        runTest {
            val result = offlineEmailVerifier.verify("maksym.balatsko@blindspot.ai")
            assertTrue(result.free is CheckResult.Passed)
        }

    @Test
    fun `offline registrability fails for invalid domain`() =
        runTest {
            val result = offlineEmailVerifier.verify("mbalatsko@invalid.invalid")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.registrability is CheckResult.Failed)
        }

    @Test
    fun `offline disposability fails for disposable domain`() =
        runTest {
            val result = offlineEmailVerifier.verify("mbalatsko@simplelogin.com")
            assertFalse(result.isLikelyDeliverable())
            assertTrue(result.disposable is CheckResult.Failed)
        }

    @Test
    fun `offline role-based check fails for role-based username`() =
        runTest {
            val result = offlineEmailVerifier.verify("admin@simplelogin.com")
            assertTrue(result.roleBasedUsername is CheckResult.Failed)
        }
}
