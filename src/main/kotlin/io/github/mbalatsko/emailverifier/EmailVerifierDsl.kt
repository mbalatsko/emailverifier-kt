package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.providers.OfflineLFDomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Configuration for the Public Suffix List (PSL) check.
 *
 * @property enabled whether this check should be performed.
 * @property pslUrl URL to the Public Suffix List data file.
 * @property offline whether to use the bundled offline PSL data.
 */
data class RegistrabilityConfig(
    val enabled: Boolean = true,
    val pslUrl: String = PslIndex.MOZILLA_PSL_URL,
    val offline: Boolean = true,
)

/**
 * Builder for [RegistrabilityConfig].
 */
class RegistrabilityConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the Public Suffix List data file. */
    var pslUrl: String = PslIndex.MOZILLA_PSL_URL

    /** Whether to use the bundled offline PSL data. */
    var offline: Boolean = true

    internal fun build(allOffline: Boolean) = RegistrabilityConfig(enabled, pslUrl, offline || allOffline)
}

/**
 * Configuration for MX record check.
 *
 * @property enabled whether this check should be performed.
 * @property dohServerEndpoint URL to the DNS-over-HTTPS server.
 */
data class MxRecordConfig(
    val enabled: Boolean = true,
    val dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL,
)

/**
 * Builder for [MxRecordConfig].
 */
class MxRecordConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the DNS-over-HTTPS server. */
    var dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL

    internal fun build(allOffline: Boolean) = MxRecordConfig(enabled && !allOffline, dohServerEndpoint)
}

/**
 * Configuration for disposable email check.
 *
 * @property enabled whether this check should be performed.
 * @property domainsListUrl URL to the disposable email domains list.
 * @property offline whether to use the bundled offline disposable email domains list.
 */
data class DisposabilityConfig(
    val enabled: Boolean = true,
    val domainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL,
    val offline: Boolean = true,
)

/**
 * Builder for [DisposabilityConfig].
 */
class DisposabilityConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the disposable email domains list. */
    var domainsListUrl: String = DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_URL

    /** Whether to use the bundled offline disposable email domains list. */
    var offline: Boolean = true

    internal fun build(allOffline: Boolean) = DisposabilityConfig(enabled, domainsListUrl, offline || allOffline)
}

/**
 * Configuration for Gravatar check.
 *
 * @property enabled whether this check should be performed.
 */
data class GravatarConfig(
    val enabled: Boolean = true,
)

/**
 * Builder for [GravatarConfig].
 */
class GravatarConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    internal fun build(allOffline: Boolean) = GravatarConfig(enabled && !allOffline)
}

/**
 * Configuration for free email provider check.
 *
 * @property enabled whether this check should be performed.
 * @property domainsListUrl URL to the free email provider domains list.
 * @property offline whether to use the bundled offline free email provider domains list.
 */
data class FreeConfig(
    val enabled: Boolean = true,
    val domainsListUrl: String = FreeChecker.FREE_EMAILS_LIST_URL,
    val offline: Boolean = true,
)

/**
 * Builder for [FreeConfig].
 */
class FreeConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the free email provider domains list. */
    var domainsListUrl: String = FreeChecker.FREE_EMAILS_LIST_URL

    /** Whether to use the bundled offline free email provider domains list. */
    var offline: Boolean = true

    internal fun build(allOffline: Boolean) = FreeConfig(enabled, domainsListUrl, offline || allOffline)
}

/**
 * Configuration for role-based username check.
 *
 * @property enabled whether this check should be performed.
 * @property usernamesListUrl URL to the role-based usernames list.
 * @property offline whether to use the bundled offline role-based usernames list.
 */
data class RoleBasedUsernameConfig(
    val enabled: Boolean = true,
    val usernamesListUrl: String = RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_URL,
    val offline: Boolean = true,
)

/**
 * Builder for [RoleBasedUsernameConfig].
 */
class RoleBasedUsernameConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the role-based usernames list. */
    var usernamesListUrl: String = RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_URL

    /** Whether to use the bundled offline role-based usernames list. */
    var offline: Boolean = true

    internal fun build(allOffline: Boolean) = RoleBasedUsernameConfig(enabled, usernamesListUrl, offline || allOffline)
}

/**
 * DSL builder for configuring and initializing [EmailVerifier].
 */
class EmailVerifierDslBuilder {
    /**
     * If true, all checks that support offline mode will use the bundled data.
     * This will also disable checks that do not support offline mode.
     */
    var allOffline = false
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

            val registrabilityConfig = registrability.build(allOffline)
            val mxRecordConfig = mxRecord.build(allOffline)
            val disposabilityConfig = disposability.build(allOffline)
            val gravatarConfig = gravatar.build(allOffline)
            val freeConfig = free.build(allOffline)
            val roleBasedUsernameConfig = roleBasedUsername.build(allOffline)

            val pslIndex =
                when (registrabilityConfig.enabled) {
                    true ->
                        async {
                            val domainProvider =
                                when (registrabilityConfig.offline) {
                                    true -> OfflineLFDomainsProvider(PslIndex.MOZILLA_PSL_RESOURCE_FILE)
                                    false -> OnlineLFDomainsProvider(registrabilityConfig.pslUrl, currentHttpClient)
                                }
                            PslIndex.init(domainProvider)
                        }
                    false -> null
                }

            val disposableEmailChecker =
                when (disposabilityConfig.enabled) {
                    true ->
                        async {
                            val domainProvider =
                                when (disposabilityConfig.offline) {
                                    true -> OfflineLFDomainsProvider(DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE)
                                    false -> OnlineLFDomainsProvider(disposabilityConfig.domainsListUrl, currentHttpClient)
                                }
                            DisposableEmailChecker.init(
                                domainProvider,
                            )
                        }
                    false -> null
                }

            val freeChecker =
                when (freeConfig.enabled) {
                    true ->
                        async {
                            val domainProvider =
                                when (freeConfig.offline) {
                                    true -> OfflineLFDomainsProvider(FreeChecker.FREE_EMAILS_LIST_RESOURCE_FILE)
                                    false -> OnlineLFDomainsProvider(freeConfig.domainsListUrl, currentHttpClient)
                                }
                            FreeChecker.init(domainProvider)
                        }
                    false -> null
                }

            val roleBasedUsernameChecker =
                when (roleBasedUsernameConfig.enabled) {
                    true ->
                        async {
                            val domainProvider =
                                when (freeConfig.offline) {
                                    true -> OfflineLFDomainsProvider(RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE)
                                    false -> OnlineLFDomainsProvider(roleBasedUsernameConfig.usernamesListUrl, currentHttpClient)
                                }
                            RoleBasedUsernameChecker.init(
                                domainProvider,
                            )
                        }
                    false -> null
                }

            val mxRecordChecker =
                when (mxRecordConfig.enabled) {
                    true ->
                        MxRecordChecker(
                            GoogleDoHLookupBackend(
                                currentHttpClient,
                                mxRecordConfig.dohServerEndpoint,
                            ),
                        )
                    false -> null
                }

            val gravatarChecker =
                when (gravatarConfig.enabled) {
                    true -> GravatarChecker(currentHttpClient)
                    false -> null
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
