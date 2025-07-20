package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.HostnameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecord
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityChecker
import io.github.mbalatsko.emailverifier.components.checkers.UsernameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.core.CheckResult
import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.github.mbalatsko.emailverifier.components.core.DnsLookupBackend
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
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

    private suspend fun buildVerifier(
        mxRecords: List<MxRecord>,
        disposableAllow: Set<String> = emptySet(),
        disposableDeny: Set<String> = emptySet(),
        freeAllow: Set<String> = emptySet(),
        freeDeny: Set<String> = emptySet(),
        roleAllow: Set<String> = emptySet(),
        roleDeny: Set<String> = emptySet(),
    ): EmailVerifier =
        EmailVerifier(
            emailSyntaxChecker = EmailSyntaxChecker(),
            registrabilityChecker = RegistrabilityChecker.create(FixedListProvider(setOf("com", "co.uk"))),
            mxRecordChecker = MxRecordChecker(FakeDnsBackend(mxRecords)),
            disposableEmailChecker =
                HostnameInDatasetChecker.create(
                    FixedListProvider(setOf("disposable.com")),
                    disposableAllow,
                    disposableDeny,
                ),
            gravatarChecker = null,
            freeChecker = HostnameInDatasetChecker.create(FixedListProvider(setOf("free.com")), freeAllow, freeDeny),
            roleBasedUsernameChecker = UsernameInDatasetChecker.create(FixedListProvider(setOf("role")), roleAllow, roleDeny),
            smtpChecker = null,
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

    @Test
    fun `allow and deny sets work as expected`() =
        runTest {
            val verifier =
                buildVerifier(
                    mxRecords = listOf(MxRecord("mx.example.com", 10)),
                    disposableAllow = setOf("disposable.com"),
                    freeDeny = setOf("example.com"),
                    roleAllow = setOf("role"),
                )

            var result = verifier.verify("user@disposable.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.disposable is CheckResult.Passed)

            result = verifier.verify("user@example.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.free is CheckResult.Failed)

            result = verifier.verify("role@example.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.roleBasedUsername is CheckResult.Passed)
        }

    private class ErrorDnsBackend : DnsLookupBackend {
        override suspend fun getMxRecords(hostname: String): List<MxRecord> = throw ConnectionError("test error")
    }

    @Test
    fun `check returns errored on backend error`() =
        runTest {
            val verifier =
                EmailVerifier(
                    emailSyntaxChecker = EmailSyntaxChecker(),
                    registrabilityChecker = null,
                    mxRecordChecker = MxRecordChecker(ErrorDnsBackend()),
                    disposableEmailChecker = null,
                    gravatarChecker = null,
                    freeChecker = null,
                    roleBasedUsernameChecker = null,
                    smtpChecker = null,
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

    @Test
    fun `throws for improper configuration - both resourcesFilePath and filePath are null`() =
        runTest {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    registrability {
                        offline = true
                        resourcesFilePath = null
                        filePath = null
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    free {
                        offline = true
                        resourcesFilePath = null
                        filePath = null
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    disposability {
                        offline = true
                        resourcesFilePath = null
                        filePath = null
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    roleBasedUsername {
                        offline = true
                        resourcesFilePath = null
                        filePath = null
                    }
                }
            }
        }

    @Test
    fun `throws for improper configuration - both resourcesFilePath and filePath are set`() =
        runTest {
            // ensure input file exists
            val tempFile = createTempFile(prefix = "domains", suffix = ".txt")

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    registrability {
                        offline = true
                        resourcesFilePath = RegistrabilityChecker.MOZILLA_PSL_RESOURCE_FILE
                        filePath = tempFile.absolutePathString()
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    free {
                        offline = true
                        resourcesFilePath = Constants.FREE_EMAILS_LIST_RESOURCE_FILE
                        filePath = tempFile.absolutePathString()
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    disposability {
                        offline = true
                        resourcesFilePath = Constants.DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE
                        filePath = tempFile.absolutePathString()
                    }
                }
            }

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                emailVerifier {
                    roleBasedUsername {
                        offline = true
                        resourcesFilePath = Constants.ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE
                        filePath = tempFile.absolutePathString()
                    }
                }
            }

            // cleanup
            tempFile.deleteExisting()
        }
}

class EmailVerifierOnlineTest {
    private val onlineEmailVerifier =
        runBlocking {
            emailVerifier {
                smtp {
                    enabled = true
                    maxRetries = 1
                }
            }
        }

    @Test
    fun `gmail passes main checks but fails free and gravatar`() =
        runTest {
            val result = onlineEmailVerifier.verify("mbalatsko@gmail.com")
            assertTrue(result.isLikelyDeliverable())
            assertTrue(result.gravatar is CheckResult.Failed<*>)
            assertTrue(result.free is CheckResult.Failed<*>)
            assertTrue(result.roleBasedUsername is CheckResult.Passed<*>)
            assertTrue(result.smtp is CheckResult.Passed)
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
            assertTrue(result.smtp is CheckResult.Skipped)
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
