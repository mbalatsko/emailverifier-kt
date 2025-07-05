package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Configuration for the Public Suffix List (PSL) check.
 */
data class RegistrabilityConfig(
    val enabled: Boolean = true,
    val pslUrl: String = PslIndex.MOZILLA_PSL_URL,
)

/**
 * Builder for [RegistrabilityConfig].
 */
class RegistrabilityConfigBuilder {
    var enabled: Boolean = true
    var pslUrl: String = PslIndex.MOZILLA_PSL_URL

    internal fun build() = RegistrabilityConfig(enabled, pslUrl)
}

/**
 * Configuration for MX record check.
 */
data class MxRecordConfig(
    val enabled: Boolean = true,
    val dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL,
)

/**
 * Builder for [MxRecordConfig].
 */
class MxRecordConfigBuilder {
    var enabled: Boolean = true
    var dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL

    internal fun build() = MxRecordConfig(enabled, dohServerEndpoint)
}

/**
 * Configuration for disposable email check.
 */
data class DisposabilityConfig(
    val enabled: Boolean = true,
    val domainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL,
)

/**
 * Builder for [DisposabilityConfig].
 */
class DisposabilityConfigBuilder {
    var enabled: Boolean = true
    var domainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL

    internal fun build() = DisposabilityConfig(enabled, domainsListUrl)
}

/**
 * Configuration for Gravatar check.
 */
data class GravatarConfig(
    val enabled: Boolean = true,
)

/**
 * Builder for [GravatarConfig].
 */
class GravatarConfigBuilder {
    var enabled: Boolean = true

    internal fun build() = GravatarConfig(enabled)
}

/**
 * Configuration for free email provider check.
 */
data class FreeConfig(
    val enabled: Boolean = true,
    val domainsListUrl: String = FreeChecker.FREE_EMAILS_LIST_URL,
)

/**
 * Builder for [FreeConfig].
 */
class FreeConfigBuilder {
    var enabled: Boolean = true
    var domainsListUrl: String = FreeChecker.FREE_EMAILS_LIST_URL

    internal fun build() = FreeConfig(enabled, domainsListUrl)
}

/**
 * Configuration for role-based username check.
 */
data class RoleBasedUsernameConfig(
    val enabled: Boolean = true,
    val usernamesListUrl: String = RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_URL,
)

/**
 * Builder for [RoleBasedUsernameConfig].
 */
class RoleBasedUsernameConfigBuilder {
    var enabled: Boolean = true
    var usernamesListUrl: String = RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_URL

    internal fun build() = RoleBasedUsernameConfig(enabled, usernamesListUrl)
}

/**
 * DSL builder for configuring and initializing [EmailVerifier].
 */
class EmailVerifierDslBuilder {
    val registrability = RegistrabilityConfigBuilder()
    val mxRecord = MxRecordConfigBuilder()
    val disposability = DisposabilityConfigBuilder()
    val gravatar = GravatarConfigBuilder()
    val free = FreeConfigBuilder()
    val roleBasedUsername = RoleBasedUsernameConfigBuilder()

    var httpClient: HttpClient? = null

    fun registrability(block: RegistrabilityConfigBuilder.() -> Unit) {
        registrability.apply(block)
    }

    fun mxRecord(block: MxRecordConfigBuilder.() -> Unit) {
        mxRecord.apply(block)
    }

    fun disposability(block: DisposabilityConfigBuilder.() -> Unit) {
        disposability.apply(block)
    }

    fun gravatar(block: GravatarConfigBuilder.() -> Unit) {
        gravatar.apply(block)
    }

    fun free(block: FreeConfigBuilder.() -> Unit) {
        free.apply(block)
    }

    fun roleBasedUsername(block: RoleBasedUsernameConfigBuilder.() -> Unit) {
        roleBasedUsername.apply(block)
    }

    internal suspend fun build(): EmailVerifier =
        coroutineScope {
            val currentHttpClient = httpClient ?: HttpClient(CIO)
            val emailSyntaxChecker = EmailSyntaxChecker()

            val registrabilityConfig = registrability.build()
            val mxRecordConfig = mxRecord.build()
            val disposabilityConfig = disposability.build()
            val gravatarConfig = gravatar.build()
            val freeConfig = free.build()
            val roleBasedUsernameConfig = roleBasedUsername.build()

            val pslIndex =
                if (registrabilityConfig.enabled) {
                    async { PslIndex.init(OnlineLFDomainsProvider(registrabilityConfig.pslUrl, currentHttpClient)) }
                } else {
                    null
                }

            val disposableEmailChecker =
                if (disposabilityConfig.enabled) {
                    async {
                        DisposableEmailChecker.init(
                            OnlineLFDomainsProvider(disposabilityConfig.domainsListUrl, currentHttpClient),
                        )
                    }
                } else {
                    null
                }

            val freeChecker =
                if (freeConfig.enabled) {
                    async { FreeChecker.init(OnlineLFDomainsProvider(freeConfig.domainsListUrl, currentHttpClient)) }
                } else {
                    null
                }

            val roleBasedUsernameChecker =
                if (roleBasedUsernameConfig.enabled) {
                    async {
                        RoleBasedUsernameChecker.init(OnlineLFDomainsProvider(roleBasedUsernameConfig.usernamesListUrl, currentHttpClient))
                    }
                } else {
                    null
                }

            val mxRecordChecker =
                if (mxRecordConfig.enabled) {
                    MxRecordChecker(GoogleDoHLookupBackend(currentHttpClient, mxRecordConfig.dohServerEndpoint))
                } else {
                    null
                }

            val gravatarChecker =
                if (gravatarConfig.enabled) {
                    GravatarChecker(currentHttpClient)
                } else {
                    null
                }

            EmailVerifier(
                emailSyntaxChecker,
                pslIndex?.await(),
                mxRecordChecker,
                disposableEmailChecker?.await(),
                gravatarChecker,
                freeChecker?.await(),
                roleBasedUsernameChecker?.await(),
            )
        }
}

/**
 * Initializes a new [EmailVerifier] with a DSL-based configuration.
 *
 * @param block a lambda with [EmailVerifierDslBuilder] as its receiver to configure the verifier.
 * @return an initialized [EmailVerifier] ready for use.
 */
suspend fun emailVerifier(block: EmailVerifierDslBuilder.() -> Unit): EmailVerifier = EmailVerifierDslBuilder().apply(block).build()
