package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.HostnameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityChecker
import io.github.mbalatsko.emailverifier.components.checkers.UsernameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.core.CheckResult
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class EmailVerifierDataUpdateTest {
    private class MutableSetProvider(
        private var items: Set<String>,
    ) : DomainsProvider {
        override suspend fun provide(): Set<String> = items

        fun add(item: String) {
            items = items + item
        }
    }

    @Test
    fun `updateAllData refreshes all checkers`() =
        runTest {
            val pslProvider = MutableSetProvider(setOf("com"))
            val disposableProvider = MutableSetProvider(setOf("disposable.com"))
            val freeProvider = MutableSetProvider(setOf("free.com"))
            val roleBasedProvider = MutableSetProvider(setOf("role"))

            val verifier =
                EmailVerifier(
                    emailSyntaxChecker =
                        io.github.mbalatsko.emailverifier.components.checkers
                            .EmailSyntaxChecker(),
                    registrabilityChecker = RegistrabilityChecker.create(pslProvider),
                    mxRecordChecker = null,
                    disposableEmailChecker = HostnameInDatasetChecker.create(disposableProvider, emptySet(), emptySet()),
                    gravatarChecker = null,
                    freeChecker = HostnameInDatasetChecker.create(freeProvider, emptySet(), emptySet()),
                    roleBasedUsernameChecker = UsernameInDatasetChecker.create(roleBasedProvider, emptySet(), emptySet()),
                    smtpChecker = null,
                )

            var result = verifier.verify("user@newdisposable.com")
            assertIs<CheckResult.Passed<*>>(result.disposable)

            result = verifier.verify("user@newfree.com")
            assertIs<CheckResult.Passed<*>>(result.free)

            result = verifier.verify("newrole@example.com")
            assertIs<CheckResult.Passed<*>>(result.roleBasedUsername)

            result = verifier.verify("user@something.newtld")
            assertIs<CheckResult.Failed<*>>(result.registrability)

            pslProvider.add("newtld")
            disposableProvider.add("newdisposable.com")
            freeProvider.add("newfree.com")
            roleBasedProvider.add("newrole")

            verifier.updateAllData()

            result = verifier.verify("user@newdisposable.com")
            assertIs<CheckResult.Failed<*>>(result.disposable)

            result = verifier.verify("user@newfree.com")
            assertIs<CheckResult.Failed<*>>(result.free)

            result = verifier.verify("newrole@example.com")
            assertIs<CheckResult.Failed<*>>(result.roleBasedUsername)

            result = verifier.verify("user@something.newtld")
            assertIs<CheckResult.Passed<*>>(result.registrability)
        }
}
