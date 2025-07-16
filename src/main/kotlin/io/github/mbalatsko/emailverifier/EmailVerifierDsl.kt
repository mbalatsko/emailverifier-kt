package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.HostnameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpChecker
import io.github.mbalatsko.emailverifier.components.checkers.UsernameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.core.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.core.SocketSmtpConnection
import io.github.mbalatsko.emailverifier.components.providers.OfflineLFDomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.net.Proxy

/**
 * Configuration for the Public Suffix List (PSL) check.
 *
 * @property enabled whether this check should be performed.
 * @property pslUrl URL to the Public Suffix List data file.
 * @property offline whether to use the bundled offline PSL data.
 */
data class RegistrabilityConfig(
    val enabled: Boolean,
    val pslUrl: String,
    val offline: Boolean,
)

/**
 * Builder for [RegistrabilityConfig].
 */
class RegistrabilityConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the Public Suffix List data file. */
    var pslUrl: String = RegistrabilityChecker.MOZILLA_PSL_URL

    /** Whether to use the bundled offline PSL data. */
    var offline: Boolean = false

    internal fun build() = RegistrabilityConfig(enabled, pslUrl, offline)
}

/**
 * Configuration for MX record check.
 *
 * @property enabled whether this check should be performed.
 * @property dohServerEndpoint URL to the DNS-over-HTTPS server.
 */
data class MxRecordConfig(
    val enabled: Boolean,
    val dohServerEndpoint: String,
)

/**
 * Builder for [MxRecordConfig].
 */
class MxRecordConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the DNS-over-HTTPS server. */
    var dohServerEndpoint: String = GoogleDoHLookupBackend.GOOGLE_DOH_URL

    internal fun build() = MxRecordConfig(enabled, dohServerEndpoint)
}

/**
 * Configuration for disposable email check.
 *
 * @property enabled whether this check should be performed.
 * @property domainsListUrl URL to the disposable email domains list.
 * @property offline whether to use the bundled offline disposable email domains list.
 * @property allow a set of domains to be treated as not disposable.
 * @property deny a set of domains to be treated as disposable.
 */
data class DisposabilityConfig(
    val enabled: Boolean,
    val domainsListUrl: String,
    val offline: Boolean,
    val allow: Set<String>,
    val deny: Set<String>,
)

/**
 * Builder for [DisposabilityConfig].
 */
class DisposabilityConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the disposable email domains list. */
    var domainsListUrl: String = Constants.DISPOSABLE_EMAILS_LIST_STRICT_URL

    /** Whether to use the bundled offline disposable email domains list. */
    var offline: Boolean = false

    /** A set of domains to be treated as not disposable. */
    var allow: Set<String> = emptySet()

    /** A set of domains to be treated as disposable. */
    var deny: Set<String> = emptySet()

    internal fun build() = DisposabilityConfig(enabled, domainsListUrl, offline, allow, deny)
}

/**
 * Configuration for Gravatar check.
 *
 * @property enabled whether this check should be performed.
 */
data class GravatarConfig(
    val enabled: Boolean,
)

/**
 * Builder for [GravatarConfig].
 */
class GravatarConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    internal fun build() = GravatarConfig(enabled)
}

/**
 * Configuration for free email provider check.
 *
 * @property enabled whether this check should be performed.
 * @property domainsListUrl URL to the free email provider domains list.
 * @property offline whether to use the bundled offline free email provider domains list.
 * @property allow a set of domains to be treated as not free.
 * @property deny a set of domains to be treated as free.
 */
data class FreeConfig(
    val enabled: Boolean,
    val domainsListUrl: String,
    val offline: Boolean,
    val allow: Set<String>,
    val deny: Set<String>,
)

/**
 * Builder for [FreeConfig].
 */
class FreeConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the free email provider domains list. */
    var domainsListUrl: String = Constants.FREE_EMAILS_LIST_URL

    /** Whether to use the bundled offline free email provider domains list. */
    var offline: Boolean = false

    /** A set of domains to be treated as not free. */
    var allow: Set<String> = emptySet()

    /** A set of domains to be treated as free. */
    var deny: Set<String> = emptySet()

    internal fun build() = FreeConfig(enabled, domainsListUrl, offline, allow, deny)
}

/**
 * Configuration for role-based username check.
 *
 * @property enabled whether this check should be performed.
 * @property usernamesListUrl URL to the role-based usernames list.
 * @property offline whether to use the bundled offline role-based usernames list.
 * @property allow a set of usernames to be treated as not role-based.
 * @property deny a set of usernames to be treated as role-based.
 */
data class RoleBasedUsernameConfig(
    val enabled: Boolean,
    val usernamesListUrl: String,
    val offline: Boolean,
    val allow: Set<String>,
    val deny: Set<String>,
)

/**
 * Builder for [RoleBasedUsernameConfig].
 */
class RoleBasedUsernameConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** URL to the role-based usernames list. */
    var usernamesListUrl: String = Constants.ROLE_BASED_USERNAMES_LIST_URL

    /** Whether to use the bundled offline role-based usernames list. */
    var offline: Boolean = false

    /** A set of usernames to be treated as not role-based. */
    var allow: Set<String> = emptySet()

    /** A set of usernames to be treated as role-based. */
    var deny: Set<String> = emptySet()

    internal fun build() = RoleBasedUsernameConfig(enabled, usernamesListUrl, offline, allow, deny)
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

    internal fun build() =
        SmtpConfig(
            enabled,
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
    companion object {
        private val logger = LoggerFactory.getLogger(EmailVerifierDslBuilder::class.java)
    }

    /**
     * If true, all checks that support offline mode will use the bundled data.
     * This will also disable checks that do not support offline mode.
     */
    var allOffline = false

    /**
     * A custom [HttpClient] to be used for all network operations.
     * If not provided, a default client with a retry policy will be used.
     */
    var httpClient: HttpClient? = null

    val registrability = RegistrabilityConfigBuilder()
    val mxRecord = MxRecordConfigBuilder()
    val disposability = DisposabilityConfigBuilder()
    val gravatar = GravatarConfigBuilder()
    val free = FreeConfigBuilder()
    val roleBasedUsername = RoleBasedUsernameConfigBuilder()
    val smtp = SmtpConfigBuilder()

    /**
     * Configures the Public Suffix List (PSL) check.
     * @param block a lambda with [RegistrabilityConfigBuilder] as its receiver.
     */
    fun registrability(block: RegistrabilityConfigBuilder.() -> Unit) {
        registrability.apply(block)
    }

    /**
     * Configures the MX record check.
     * @param block a lambda with [MxRecordConfigBuilder] as its receiver.
     */
    fun mxRecord(block: MxRecordConfigBuilder.() -> Unit) {
        mxRecord.apply(block)
    }

    /**
     * Configures the disposable email check.
     * @param block a lambda with [DisposabilityConfigBuilder] as its receiver.
     */
    fun disposability(block: DisposabilityConfigBuilder.() -> Unit) {
        disposability.apply(block)
    }

    /**
     * Configures the Gravatar check.
     * @param block a lambda with [GravatarConfigBuilder] as its receiver.
     */
    fun gravatar(block: GravatarConfigBuilder.() -> Unit) {
        gravatar.apply(block)
    }

    /**
     * Configures the free email provider check.
     * @param block a lambda with [FreeConfigBuilder] as its receiver.
     */
    fun free(block: FreeConfigBuilder.() -> Unit) {
        free.apply(block)
    }

    /**
     * Configures the role-based username check.
     * @param block a lambda with [RoleBasedUsernameConfigBuilder] as its receiver.
     */
    fun roleBasedUsername(block: RoleBasedUsernameConfigBuilder.() -> Unit) {
        roleBasedUsername.apply(block)
    }

    /**
     * Configures the SMTP check.
     * @param block a lambda with [SmtpConfigBuilder] as its receiver.
     */
    fun smtp(block: SmtpConfigBuilder.() -> Unit) {
        smtp.apply(block)
    }

    internal suspend fun build(): EmailVerifier =
        coroutineScope {
            logger.debug("Building EmailVerifier...")

            val currentHttpClient =
                httpClient
                    ?: HttpClient(CIO) {
                        install(HttpRequestRetry) {
                            retryOnServerErrors(maxRetries = 3)
                            exponentialDelay()
                        }
                    }.also {
                        logger.debug("Created default HttpClient with retry policy.")
                    }
            val emailSyntaxChecker = EmailSyntaxChecker()

            val registrabilityConfig = registrability.build().let { if (allOffline) it.copy(offline = true) else it }
            logger.debug("Registrability config: {}", registrabilityConfig)
            val mxRecordConfig = mxRecord.build().let { if (allOffline) it.copy(enabled = false) else it }
            logger.debug("MX record config: {}", mxRecordConfig)
            val disposabilityConfig = disposability.build().let { if (allOffline) it.copy(offline = true) else it }
            logger.debug("Disposability config: {}", disposabilityConfig)
            val gravatarConfig = gravatar.build().let { if (allOffline) it.copy(enabled = false) else it }
            logger.debug("Gravatar config: {}", gravatarConfig)
            val freeConfig = free.build().let { if (allOffline) it.copy(offline = true) else it }
            logger.debug("Free email config: {}", freeConfig)
            val roleBasedUsernameConfig = roleBasedUsername.build().let { if (allOffline) it.copy(offline = true) else it }
            logger.debug("Role-based username config: {}", roleBasedUsernameConfig)
            val smtpConfig = smtp.build().let { if (allOffline) it.copy(enabled = false) else it }
            logger.debug("SMTP config: {}", smtpConfig)

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
            ).also {
                logger.debug("EmailVerifier built successfully.")
            }
        }

    /**
     * Creates a [RegistrabilityChecker] instance based on the provided configuration.
     *
     * @param config The registrability configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return [RegistrabilityChecker] or null if the check is disabled.
     */
    private suspend fun createPslIndex(
        config: RegistrabilityConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        logger.debug("Creating RegistrabilityChecker (offline: {})...", config.offline)
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(RegistrabilityChecker.MOZILLA_PSL_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.pslUrl, httpClient)
            }
        RegistrabilityChecker.create(provider)
    } else {
        logger.debug("RegistrabilityChecker is disabled.")
        null
    }

    /**
     * Creates a [HostnameInDatasetChecker] instance for disposable email checking.
     *
     * @param config The disposability configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return A [HostnameInDatasetChecker] or null if the check is disabled.
     */
    private suspend fun createDisposableEmailChecker(
        config: DisposabilityConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        logger.debug("Creating DisposableEmailChecker (offline: {})...", config.offline)
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(Constants.DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.domainsListUrl, httpClient)
            }
        HostnameInDatasetChecker.create(provider, config.allow, config.deny)
    } else {
        logger.debug("DisposableEmailChecker is disabled.")
        null
    }

    /**
     * Creates a [HostnameInDatasetChecker] instance for free email provider checking.
     *
     * @param config The free email check configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return A [HostnameInDatasetChecker] or null if the check is disabled.
     */
    private suspend fun createFreeChecker(
        config: FreeConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        logger.debug("Creating FreeChecker (offline: {})...", config.offline)
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(Constants.FREE_EMAILS_LIST_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.domainsListUrl, httpClient)
            }
        HostnameInDatasetChecker.create(provider, config.allow, config.deny)
    } else {
        logger.debug("FreeChecker is disabled.")
        null
    }

    /**
     * Creates a [UsernameInDatasetChecker] instance for role-based username checking.
     *
     * @param config The role-based username configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @return A [UsernameInDatasetChecker] or null if the check is disabled.
     */
    private suspend fun createRoleBasedUsernameChecker(
        config: RoleBasedUsernameConfig,
        httpClient: HttpClient,
    ) = if (config.enabled) {
        logger.debug("Creating RoleBasedUsernameChecker (offline: {})...", config.offline)
        val provider =
            if (config.offline) {
                OfflineLFDomainsProvider(Constants.ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE)
            } else {
                OnlineLFDomainsProvider(config.usernamesListUrl, httpClient)
            }
        UsernameInDatasetChecker.create(provider, config.allow, config.deny)
    } else {
        logger.debug("RoleBasedUsernameChecker is disabled.")
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
        logger.debug("Creating MxRecordChecker...")
        MxRecordChecker(GoogleDoHLookupBackend(httpClient, config.dohServerEndpoint))
    } else {
        logger.debug("MxRecordChecker is disabled.")
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
        logger.debug("Creating GravatarChecker...")
        GravatarChecker(httpClient)
    } else {
        logger.debug("GravatarChecker is disabled.")
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
            logger.debug("Creating SmtpChecker...")
            SmtpChecker(
                config.enableAllCatchCheck,
                config.maxRetries,
                { address: String, port: Int ->
                    SocketSmtpConnection(address, port, config.timeoutMillis, config.proxy)
                },
            )
        } else {
            logger.debug("SmtpChecker is disabled.")
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
