package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.checkers.DisposableEmailChecker
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.FreeChecker
import io.github.mbalatsko.emailverifier.components.checkers.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.PslIndex
import io.github.mbalatsko.emailverifier.components.checkers.RoleBasedUsernameChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpChecker
import io.github.mbalatsko.emailverifier.components.checkers.SocketSmtpConnection
import io.github.mbalatsko.emailverifier.components.providers.OfflineLFDomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.Proxy

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
 * Configuration for SMTP check.
 *
 * @property enabled whether this check should be performed.
 * @property enableAllCatchCheck whether to check if the server has a catch-all policy.
 * @property timeoutMillis connection timeout in milliseconds.
 * @property maxRetries maximum number of retries for SMTP commands.
 * @property proxy The proxy to use for the connection, or null for a direct connection.
 */
data class SmtpConfig(
    val enabled: Boolean,
    val enableAllCatchCheck: Boolean,
    val timeoutMillis: Int,
    val maxRetries: Int,
    val proxy: Proxy?,
)

/**
 * Builder for [SmtpConfig].
 */
class SmtpConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = false

    /** Whether to check if the server has a catch-all policy. */
    var enableAllCatchCheck: Boolean = true

    /** Connection timeout in milliseconds. */
    var timeoutMillis: Int = 5000

    /** Maximum number of retries for SMTP commands. */
    var maxRetries: Int = 2

    /** The proxy to use for the connection, or null for a direct connection. */
    var proxy: Proxy? = null

    internal fun build(allOffline: Boolean) =
        SmtpConfig(
            enabled && !allOffline,
            enableAllCatchCheck,
            timeoutMillis,
            maxRetries,
            proxy,
        )
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
    val smpt = SmtpConfigBuilder()

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

    /**
     * Configures SMTP check.
     *
     * @param block a lambda with [SmtpConfigBuilder] as its receiver.
     */
    fun smtp(block: SmtpConfigBuilder.() -> Unit) {
        smpt.apply(block)
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
            val smtpConfig = smpt.build(allOffline)

            val pslIndex = async { createPslIndex(registrabilityConfig, currentHttpClient) }
            val disposableEmailChecker = async { createDisposableEmailChecker(disposabilityConfig, currentHttpClient) }
            val freeChecker = async { createFreeChecker(freeConfig, currentHttpClient) }
            val roleBasedUsernameChecker = async { createRoleBasedUsernameChecker(roleBasedUsernameConfig, currentHttpClient) }
            val mxRecordChecker = createMxRecordChecker(mxRecordConfig, currentHttpClient)
            val gravatarChecker = createGravatarChecker(gravatarConfig, currentHttpClient)
            val smtpChecker = createSmtpChecker(smtpConfig)

            EmailVerifier(
                emailSyntaxChecker,
                pslIndex.await(),
                mxRecordChecker,
                disposableEmailChecker.await(),
                gravatarChecker,
                freeChecker.await(),
                roleBasedUsernameChecker.await(),
                smtpChecker,
            )
        }

    /**
     * Creates a [PslIndex] instance based on the provided configuration.
     *
     * @param config The registrability configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return [PslIndex] or null if the check is disabled.
     */
    private suspend fun createPslIndex(
        config: RegistrabilityConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(PslIndex.MOZILLA_PSL_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.pslUrl, httpClient)
            }
        PslIndex.init(provider)
    } else {
        null
    }

    /**
     * Creates a [DisposableEmailChecker] instance based on the provided configuration.
     *
     * @param config The disposability configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return Ac[DisposableEmailChecker] or null if the check is disabled.
     */
    private suspend fun createDisposableEmailChecker(
        config: DisposabilityConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(DisposableEmailChecker.DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.domainsListUrl, httpClient)
            }
        DisposableEmailChecker.init(provider)
    } else {
        null
    }

    /**
     * Creates a [FreeChecker] instance based on the provided configuration.
     *
     * @param config The free email check configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return [FreeChecker] or null if the check is disabled.
     */
    private suspend fun createFreeChecker(
        config: FreeConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(FreeChecker.FREE_EMAILS_LIST_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.domainsListUrl, httpClient)
            }
        FreeChecker.init(provider)
    } else {
        null
    }

    /**
     * Creates a [RoleBasedUsernameChecker] instance based on the provided configuration.
     *
     * @param config The role-based username configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return [RoleBasedUsernameChecker] or null if the check is disabled.
     */
    private suspend fun createRoleBasedUsernameChecker(
        config: RoleBasedUsernameConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(RoleBasedUsernameChecker.ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.usernamesListUrl, httpClient)
            }
        RoleBasedUsernameChecker.init(provider)
    } else {
        null
    }

    /**
     * Creates an [MxRecordChecker] instance based on the provided configuration.
     *
     * @param config The MX record configuration.
     * @param httpClient The HTTP client for DNS-over-HTTPS queries.
     * @return An [MxRecordChecker] or null if the check is disabled.
     */
    private fun createMxRecordChecker(
        config: MxRecordConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        MxRecordChecker(GoogleDoHLookupBackend(httpClient, config.dohServerEndpoint))
    } else {
        null
    }

    /**
     * Creates a [GravatarChecker] instance based on the provided configuration.
     *
     * @param config The Gravatar configuration.
     * @param httpClient The HTTP client for Gravatar requests.
     * @return A [GravatarChecker] or null if the check is disabled.
     */
    private fun createGravatarChecker(
        config: GravatarConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        GravatarChecker(httpClient)
    } else {
        null
    }

    /**
     * Creates a [SmtpChecker] instance based on the provided configuration.
     *
     * @param config The SMTP check configuration.
     * @return A [SmtpChecker] or null if the check is disabled.
     */
    private fun createSmtpChecker(config: SmtpConfig) =
        if (config.enabled) {
            SmtpChecker(
                config.enableAllCatchCheck,
                config.maxRetries,
                { address: String, port: Int ->
                    SocketSmtpConnection(address, port, config.timeoutMillis, config.proxy)
                },
            )
        } else {
            null
        }
}

/**
 * Initializes a new [EmailVerifier] with a DSL-based configuration.
 *
 * @param block a lambda with [EmailVerifierDslBuilder] as its receiver to configure the verifier.
 * @return an initialized [EmailVerifier] ready for use.
 */
suspend fun emailVerifier(block: EmailVerifierDslBuilder.() -> Unit): EmailVerifier = EmailVerifierDslBuilder().apply(block).build()
