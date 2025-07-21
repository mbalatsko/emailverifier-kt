package io.github.mbalatsko.emailverifier

import io.github.mbalatsko.emailverifier.components.Constants
import io.github.mbalatsko.emailverifier.components.checkers.EmailSyntaxChecker
import io.github.mbalatsko.emailverifier.components.checkers.GravatarChecker
import io.github.mbalatsko.emailverifier.components.checkers.HostnameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.checkers.IChecker
import io.github.mbalatsko.emailverifier.components.checkers.MxRecordChecker
import io.github.mbalatsko.emailverifier.components.checkers.RegistrabilityChecker
import io.github.mbalatsko.emailverifier.components.checkers.SmtpChecker
import io.github.mbalatsko.emailverifier.components.checkers.UsernameInDatasetChecker
import io.github.mbalatsko.emailverifier.components.core.GoogleDoHLookupBackend
import io.github.mbalatsko.emailverifier.components.core.SocketSmtpConnection
import io.github.mbalatsko.emailverifier.components.providers.DomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.FileLFDomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.OnlineLFDomainsProvider
import io.github.mbalatsko.emailverifier.components.providers.ResourceFileLFDomainsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.net.Proxy

/**
 * A sealed interface representing the source of data for a check.
 */
sealed interface DataSource {
    /** A data source from a remote URL. */
    data class Remote(
        val url: String,
    ) : DataSource

    /** A data source from a classpath resource. */
    data class Resource(
        val path: String,
    ) : DataSource

    /** A data source from a local file. */
    data class File(
        val path: String,
    ) : DataSource
}

/**
 * Configuration for the Public Suffix List (PSL) check.
 *
 * @property enabled whether this check should be performed.
 * @property source The data source for the PSL data.
 */
data class RegistrabilityConfig(
    val enabled: Boolean,
    val source: DataSource,
)

/**
 * Builder for [RegistrabilityConfig].
 */
class RegistrabilityConfigBuilder {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** The data source for the PSL data. Defaults to a remote URL. */
    var source: DataSource = DataSource.Remote(RegistrabilityChecker.MOZILLA_PSL_URL)

    /**
     * A convenience property to switch between the default remote and offline sources.
     * Setting this to `true` sets the `source` to the default bundled resource file.
     * Setting it to `false` resets the `source` to the default remote URL.
     */
    var offline: Boolean = false
        set(value) {
            field = value
            source =
                if (value) {
                    DataSource.Resource(RegistrabilityChecker.MOZILLA_PSL_RESOURCE_FILE)
                } else {
                    DataSource.Remote(RegistrabilityChecker.MOZILLA_PSL_URL)
                }
        }

    internal fun build() = RegistrabilityConfig(enabled, source)
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
 * A generic configuration for dataset-based checks.
 *
 * @property enabled whether this check should be performed.
 * @property source The data source for the dataset.
 * @property allow a set of values to be treated as valid.
 * @property deny a set of values to be treated as invalid.
 */
data class DatasetConfig(
    val enabled: Boolean,
    val source: DataSource,
    val allow: Set<String>,
    val deny: Set<String>,
)

/**
 * A generic builder for [DatasetConfig].
 */
class DatasetConfigBuilder(
    private val defaultRemoteUrl: String,
    private val defaultResourcePath: String,
) {
    /** Whether this check should be performed. */
    var enabled: Boolean = true

    /** The data source for the dataset. Defaults to a remote URL. */
    var source: DataSource = DataSource.Remote(defaultRemoteUrl)

    /**
     * A convenience property to switch between the default remote and offline sources.
     * Setting this to `true` sets the `source` to the default bundled resource file.
     * Setting it to `false` resets the `source` to the default remote URL.
     */
    var offline: Boolean = false
        set(value) {
            field = value
            source =
                if (value) {
                    DataSource.Resource(defaultResourcePath)
                } else {
                    DataSource.Remote(defaultRemoteUrl)
                }
        }

    /** A set of values to be treated as valid. */
    var allow: Set<String> = emptySet()

    /** A set of values to be treated as invalid. */
    var deny: Set<String> = emptySet()

    internal fun build() = DatasetConfig(enabled, source, allow, deny)
}

/** Configuration for disposable email check. */
typealias DisposabilityConfig = DatasetConfig

/** Builder for [DisposabilityConfig]. */
typealias DisposabilityConfigBuilder = DatasetConfigBuilder

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

/** Configuration for free email provider check. */
typealias FreeConfig = DatasetConfig

/** Builder for [FreeConfig]. */
typealias FreeConfigBuilder = DatasetConfigBuilder

/** Configuration for role-based username check. */
typealias RoleBasedUsernameConfig = DatasetConfig

/** Builder for [RoleBasedUsernameConfig]. */
typealias RoleBasedUsernameConfigBuilder = DatasetConfigBuilder

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
     * A global flag to enable offline mode for all checks.
     * When `true`, this will override individual check configurations:
     * - It forces all dataset-based checks to use their default bundled data source.
     * - It disables all network-dependent checks (MX, Gravatar, SMTP).
     */
    var allOffline = false

    /**
     * A custom [HttpClient] to be used for all network operations.
     * If not provided, a default client with a retry policy will be used.
     */
    var httpClient: HttpClient? = null

    val registrability = RegistrabilityConfigBuilder()
    val mxRecord = MxRecordConfigBuilder()
    val disposability =
        DatasetConfigBuilder(
            Constants.DISPOSABLE_EMAILS_LIST_STRICT_URL,
            Constants.DISPOSABLE_EMAILS_LIST_STRICT_RESOURCE_FILE,
        )
    val gravatar = GravatarConfigBuilder()
    val free =
        DatasetConfigBuilder(
            Constants.FREE_EMAILS_LIST_URL,
            Constants.FREE_EMAILS_LIST_RESOURCE_FILE,
        )
    val roleBasedUsername =
        DatasetConfigBuilder(
            Constants.ROLE_BASED_USERNAMES_LIST_URL,
            Constants.ROLE_BASED_USERNAMES_LIST_RESOURCE_FILE,
        )
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

            if (allOffline) {
                logger.debug("allOffline is true, overriding configurations...")
                registrability.offline = true
                disposability.offline = true
                free.offline = true
                roleBasedUsername.offline = true

                mxRecord.enabled = false
                gravatar.enabled = false
                smtp.enabled = false
            }

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

            val registrabilityConfig = registrability.build()
            logger.debug("Registrability config: {}", registrabilityConfig)
            val mxRecordConfig = mxRecord.build()
            logger.debug("MX record config: {}", mxRecordConfig)
            val disposabilityConfig = disposability.build()
            logger.debug("Disposability config: {}", disposabilityConfig)
            val gravatarConfig = gravatar.build()
            logger.debug("Gravatar config: {}", gravatarConfig)
            val freeConfig = free.build()
            logger.debug("Free email config: {}", freeConfig)
            val roleBasedUsernameConfig = roleBasedUsername.build()
            logger.debug("Role-based username config: {}", roleBasedUsernameConfig)
            val smtpConfig = smtp.build()
            logger.debug("SMTP config: {}", smtpConfig)

            val pslIndex = async { createPslIndex(registrabilityConfig, currentHttpClient) }
            val disposableEmailChecker =
                async {
                    createDatasetChecker(disposabilityConfig, currentHttpClient, "DisposableEmailChecker") { provider, allow, deny ->
                        HostnameInDatasetChecker.create(provider, allow, deny)
                    }
                }
            val freeChecker =
                async {
                    createDatasetChecker(freeConfig, currentHttpClient, "FreeChecker") { provider, allow, deny ->
                        HostnameInDatasetChecker.create(provider, allow, deny)
                    }
                }
            val roleBasedUsernameChecker =
                async {
                    createDatasetChecker(roleBasedUsernameConfig, currentHttpClient, "RoleBasedUsernameChecker") { provider, allow, deny ->
                        UsernameInDatasetChecker.create(provider, allow, deny)
                    }
                }
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

    private fun createDomainsProvider(
        source: DataSource,
        httpClient: HttpClient,
    ): DomainsProvider =
        when (source) {
            is DataSource.File -> FileLFDomainsProvider(source.path)
            is DataSource.Resource -> ResourceFileLFDomainsProvider(source.path)
            is DataSource.Remote -> OnlineLFDomainsProvider(source.url, httpClient)
        }

    /**
     * Creates a generic dataset checker.
     *
     * @param config The dataset configuration.
     * @param httpClient The HTTP client for online data fetching.
     * @param checkerName The name of the checker for logging purposes.
     * @param checkerFactory A factory function to create the checker.
     * @return A checker instance or null if the check is disabled.
     */
    private suspend fun <T : IChecker<*, *>> createDatasetChecker(
        config: DatasetConfig,
        httpClient: HttpClient,
        checkerName: String,
        checkerFactory: suspend (provider: DomainsProvider, allow: Set<String>, deny: Set<String>) -> T,
    ): T? =
        if (config.enabled) {
            logger.debug("Creating {}...", checkerName)
            val provider = createDomainsProvider(config.source, httpClient)
            checkerFactory(provider, config.allow, config.deny)
        } else {
            logger.debug("{} is disabled.", checkerName)
            null
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
        logger.debug("Creating RegistrabilityChecker...")
        val provider = createDomainsProvider(config.source, httpClient)
        RegistrabilityChecker.create(provider)
    } else {
        logger.debug("RegistrabilityChecker is disabled.")
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
