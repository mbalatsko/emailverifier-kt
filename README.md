# EmailVerifier 📬

[![Kotlin Docs](https://img.shields.io/badge/docs-kotlin-blue?logo=kotlin)](https://mbalatsko.github.io/emailverifier-kt/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mbalatsko/emailverifier-kt.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.mbalatsko/emailverifier-kt)
[![GitHub Packages](https://img.shields.io/badge/github-packages-blue)](https://github.com/mbalatsko/emailverifier-kt/packages)
[![License: MIT](https://img.shields.io/github/license/mbalatsko/emailverifier-kt)](https://github.com/mbalatsko/emailverifier-kt/blob/main/LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blue.svg?logo=kotlin)](https://kotlinlang.org/)
[![Latest Release](https://img.shields.io/github/release/mbalatsko/emailverifier-kt.svg)](https://github.com/mbalatsko/emailverifier-kt/releases)
[![Platform](https://img.shields.io/badge/platform-jvm-blue)](#)


**EmailVerifier** is a composable, pluggable Kotlin library for validating email addresses beyond just their syntax. It's built with a clear focus: help developers **reliably assess whether a given email is real, meaningful, and worth accepting**.

## ⚡️ Performance

EmailVerifier is designed for high performance and uses Kotlin's coroutines to parallelize I/O operations:

- **Parallel Initialization:** All external data sources (e.g., Public Suffix List, disposable domains) are downloaded concurrently during setup, making initialization significantly faster.
- **Parallel Verification:** Independent network checks (MX records, Gravatar) are executed concurrently for each email, reducing the verification time.

## ✅ Features

EmailVerifier performs a layered set of validations:

### 1. **Syntax Validation**
Checks the structure of the email:
- Local-part format (dot-atom and quoted-string, per RFC 5322 subset)
- Hostname validity (RFC 1035, IDNA-compliant)

### 2. **Registrability Check**
Verifies whether the email domain is **registrable**:
- Uses the [Public Suffix List](https://publicsuffix.org/)
- Returns the registrable domain (e.g., `example.co.uk` for `user@example.co.uk`) or `Failed` if not registrable (e.g., `user@something.invalid`).

### 3. **MX Record Lookup**
Ensures the domain is actually configured to receive emails:
- Queries DNS-over-HTTPS (DoH) via Google
- Returns a list of MX records or `Failed` if no MX records are found.

### 4. **Disposable Email Detection**
Filters out **temporary/disposable** email domains:
- Uses curated lists from [disposable-email-domains](https://github.com/disposable/disposable-email-domains)
- Detects domains like `mailinator.com`, `tempmail.org`, etc.
- You can also specify `allow` and `deny` sets to customize the behavior for specific domains.

### 5. **Gravatar Existence Check**

Detects whether an email has an associated **Gravatar**:
- Computes MD5 hash of the email
- Returns the Gravatar URL or `Failed` if no custom avatar is found.

### 6. **Free Email Provider Detection**
Checks whether the email domain belongs to a known free‐email provider (e.g. `gmail.com`, `yahoo.com`) 
using a curated list of popular services.
- Returns `Passed` result if email hostname is not a known free‐email provider
- You can also specify `allow` and `deny` sets to customize the behavior for specific domains.

List used: [Github gist](https://gist.github.com/okutbay/5b4974b70673dfdcc21c517632c1f984) by @okutbay 

### 7. **Role-Based Username Detection**
Detects generic or departmental username (e.g. `info@`, `admin@`, `support@`) by checking against a curated list of common role-based usernames.
- Returns `Passed` result if email username is not a known role-based username
- You can also specify `allow` and `deny` sets to customize the behavior for specific usernames.

List used: https://github.com/mbalatsko/role-based-email-addresses-list (original repo: https://github.com/mixmaxhq/role-based-email-addresses)

### 8. **SMTP Deliverability Check**
Performs a live check with the mail server to verify if the mailbox actually exists.
- Connects to the mail server and uses the `RCPT TO` command to check for deliverability without sending an email.
- Can detect "catch-all" server configurations where all emails to a domain are accepted.
- **Disabled by default**, as most cloud providers and ISPs block outbound traffic on port 25 to prevent spam. 
Can be enabled and configured to work through a SOCKS proxy.

### 9. **Offline Mode**
For environments without internet access, `EmailVerifier` can run in a fully **offline** mode. When enabled, it uses bundled 
data for checks that support it (Syntax, Registrability, Disposability, Free Email, and Role-Based Username) and automatically 
disables checks that require network access (MX Record, Gravatar, SMTP). 

You can also configure **offline mode** for each check **individually**.

The bundled data is manually updated **before release** via a GitHub Actions workflow.

## 🧪 Output: Validation Results

You get a detailed result for each check:

```kotlin
data class EmailValidationResult(
    val email: String,
    val emailParts: EmailParts,
    val syntax: CheckResult<SyntaxValidationData>,
    val registrability: CheckResult<RegistrabilityData>,
    val mx: CheckResult<MxRecordData>,
    val disposable: CheckResult<DatasetData>,
    val gravatar: CheckResult<GravatarData>,
    val free: CheckResult<DatasetData>,
    val roleBasedUsername: CheckResult<DatasetData>,
    val smtp: CheckResult<SmtpData>,
) {
    /**
     * Returns true if all strong indicator checks passed.
     * Strong indicator checks are: syntax, registrability, mx record presence, and disposability.
     * These checks are the most likely to indicate that an email address is not valid.
     */
    fun isLikelyDeliverable(): Boolean
}

/**
 * A sealed class representing the result of a single validation check.
 * It can be in one of four states: Passed, Failed, Skipped, or Errored.
 *
 * @param T the type of data carried by the result.
 */
sealed class CheckResult<out T> {
    /**
     * Indicates that the check was successful.
     * @property data data associated with the passed check.
     */
    data class Passed<T>(
        val data: T,
    ) : CheckResult<T>()

    /**
     * Indicates that the check failed.
     * @property data optional data associated with the failed check.
     */
    data class Failed<T>(
        val data: T? = null,
    ) : CheckResult<T>()

    /**
     * Indicates that the check was skipped.
     */
    data object Skipped : CheckResult<Nothing>()

    /**
     * Indicates that the check produced an error.
     * @property error the throwable that was caught during the check.
     */
    data class Errored(
        val error: Throwable,
    ) : CheckResult<Nothing>()
}

/**
 * Data class holding the validity of each part of the email syntax.
 * @property username true if the username part is valid.
 * @property plusTag true if the plus-tag part is valid.
 * @property hostname true if the hostname part is valid.
 */
data class SyntaxValidationData(
    val username: Boolean,
    val plusTag: Boolean,
    val hostname: Boolean,
)

/**
 * Data class holding the registrable domain found during the registrability check.
 * @property registrableDomain The registrable domain string, or null if not found.
 */
data class RegistrabilityData(
    val registrableDomain: String?,
)

/**
 * Data class holding the MX records found during the MX record check.
 * @property records A list of [MxRecord]s, or an empty list if none were found.
 */
data class MxRecordData(
    val records: List<MxRecord>,
)

/**
 * Data class holding the Gravatar URL found during the Gravatar check.
 * @property gravatarUrl The Gravatar URL string, or null if no custom avatar was found.
 */
data class GravatarData(
    val gravatarUrl: String?,
)

/**
 * Data class holding the result of a dataset check (disposable, free, role-based).
 *
 * @property match true if a match was found in the dataset.
 * @property matchedOn the specific entry that was matched, or null if no match was found.
 * @property source the source of the match (e.g., "allow", "deny", "default").
 */
data class DatasetData(
    val match: Boolean,
    val matchedOn: String? = null,
    val source: Source? = null,
)

/**
 * Data class holding the results of an SMTP check.
 *
 * @property isDeliverable true if the email address is deliverable.
 * @property isCatchAll true if the server has a catch-all policy, false if not, null if inconclusive.
 * @property smtpCode the last SMTP response code.
 * @property smtpMessage the last SMTP response message.
 */
data class SmtpData(
    val isDeliverable: Boolean,
    val isCatchAll: Boolean?,
    val smtpCode: Int,
    val smtpMessage: String,
)
```

Each check can return:
- `Passed` ✅ (with optional data, see data classes above for details)
- `Failed` ❌ (with optional data, see data classes above for details)
- `Errored` ⚠️ (if an unexpected error occurred during the check)
- `Skipped` ⏭️ (if not enabled or not applicable)

For `Disposable Email Detection`, `Free Email Provider Detection`, and `Role-Based Username Detection`, the result is a `CheckResult<DatasetData>`. The `Passed` state indicates the email is *not* disposable/free/role-based, while `Failed` indicates it *is*. The `DatasetData` object provides more context, including the specific rule or entry that was matched.

## 🚀 Getting Started

### 1. Add dependency (JVM only for now)

Maven:

```xml
<dependency>
    <groupId>io.github.mbalatsko</groupId>
    <artifactId>emailverifier-kt</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

Gradle:

```groovy
implementation("io.github.mbalatsko:emailverifier-kt:LATEST_VERSION")
```

Also available on [Github Packages](https://github.com/mbalatsko/emailverifier-kt/packages/2563296)

### 2. Basic usage

```kotlin
val verifier =  emailVerifier { }

val result = verifier.verify("john.doe@example.com")

if (result.isLikelyDeliverable()) {
    println("Valid email!")
} else {
    println("Email validation failed: $result")
}
```

### 3. Customizing Checks

All checks are enabled by default, but you can easily disable or configure them.

```kotlin
val verifier = emailVerifier {
    // Disable a check
    mxRecord {
        enabled = false
    }

    registrability {
        customRules = setOf(
          "my-private-tld",       // Treat .my-private-tld as a public suffix
          "*.my-private-domain",  // Treat all subdomains of .my-private-domain as public suffixes
          "!example.my-private-domain" // Make an exception to the wildcard rule
        )
    }

    // Configure allow/deny lists for dataset checks
    disposability {
        allow = setOf("my-disposable-domain.com") // Whitelist a disposable domain
        deny = setOf("my-domain.com")             // Blacklist a domain
    }

    // Configure SMTP parameters
    smtp {
        enabled = true // IMPORTANT: Disabled by default. See notes below.
        timeoutMillis = 10000 // Increase connection timeout
    }
}
```

> **⚠️ Important Note on SMTP Checks**
> The SMTP check is **disabled by default** because most Internet Service Providers (ISPs) and cloud hosting providers (like AWS, GCP, Azure) block outgoing requests on port 25 to prevent email spamming.
>
> To perform this check reliably, you will likely need to route the connection through a **SOCKS proxy** that has unrestricted access to port 25.
>
> Here is how you can configure it:
> ```kotlin
> import java.net.InetSocketAddress
> import java.net.Proxy
>
> val verifier = emailVerifier {
>     smtp {
>         enabled = true
>         // Configure a SOCKS proxy
>         proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("your-proxy-host.com", 1080))
>     }
> }
> ```

### 4. Configuring Data Sources

For checks that rely on external datasets (Registrability, Disposability, Free Email, and Role-Based Username), you have full control over the data source.

#### Global Offline Mode

For ultimate convenience, you can set the global `allOffline` flag. This forces all checks to use their bundled offline data and disables checks that require a network connection (MX, Gravatar, SMTP). This is the simplest way to configure the verifier for an environment with no internet access.

```kotlin
val verifier = emailVerifier {
    allOffline = true
}

val result = verifier.verify("mbalatsko@gmail.com")
// result.mx will be SKIPPED
// result.gravatar will be SKIPPED
// result.smtp will be SKIPPED
```

#### Per-Check Configuration

You can also configure the data source for each check individually.

##### Using Default Offline Data

The `offline` property provides a simple toggle between the default remote URL and the default bundled data source for a specific check.

```kotlin
val verifier = emailVerifier {
    // Use the bundled offline data for this check
    registrability {
        offline = true
    }

    // Use the online source for this one (default behavior)
    disposability {
        offline = false
    }
}
```

##### Using a Custom Data Source

For complete control, you can provide a custom data source using the `source` property. This is ideal for using proprietary lists, testing, or managing datasets locally. The `DataSource` type ensures your configuration is clear and type-safe.

```kotlin
import io.github.mbalatsko.emailverifier.DataSource

val verifier = emailVerifier {
    // Use a custom remote URL
    registrability {
        source = DataSource.Remote("https://my.custom.domain/public_suffix_list.dat")
    }

    // Use a custom file from your classpath resources
    disposability {
        source = DataSource.Resource("/my_disposable_domains.txt")
    }

    // Use a custom file from the local filesystem
    free {
        source = DataSource.File("/path/to/your/free_emails.txt")
    }
}
```

### 5. Advanced Configuration: Custom HttpClient

The default `HttpClient` used by `EmailVerifier` is configured with a sensible retry policy (`retryOnServerErrors(maxRetries = 3)` with exponential backoff) to handle transient network issues.

For more advanced use cases, such as adding custom headers or using a different engine, you can pass a custom-configured `HttpClient` to the `EmailVerifier`. This gives you full control over the network layer.

Here's an example of how to configure a custom client:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*

// Configure a custom HttpClient
val customHttpClient = HttpClient(CIO) {
    install(Logging) {
        level = LogLevel.INFO
    }
    // The default retry logic is not included when providing a custom client.
    // You can add it back if needed:
    // install(HttpRequestRetry) {
    //     retryOnServerErrors(maxRetries = 3)
    //     exponentialDelay()
    // }
}

// Pass the custom client in the configuration
val verifier = emailVerifier {
    httpClient = customHttpClient
}
```

### 6. Performance Considerations

The `emailVerifier {}` call performs several network requests to download the necessary data for the various checks.
To avoid re-downloading this data every time you want to verify an email, it is highly recommended to **create a single
instance of the `EmailVerifier` and reuse it throughout the lifecycle of your application**.

### 7. Dynamic Data Reloading

For long-running applications, it's often necessary to refresh the data used by the verifier without restarting the application.
`EmailVerifier` provides a set of `suspend` functions to reload the data for the checks that use external datasets.

These functions are thread-safe and will fetch the latest data from the configured `DataSource` (remote, file, or resource).

```kotlin
val verifier = emailVerifier {
    // Your configuration...
}

// Refresh the Public Suffix List data
verifier.updateRegistrabilityCheckerData()

// Refresh the disposable email domains data
verifier.updateDisposableCheckerData()

// Refresh all data sources in parallel
verifier.updateAllData()
```

This is particularly useful if you want to keep your disposable email lists or other datasets up-to-date by periodically calling these methods.

## 8. Logging

`EmailVerifier` uses the [SLF4J](https://www.slf4j.org/) logging facade. This allows you, as a user of the library, to choose your own logging framework (e.g., [Logback](http://logback.qos.ch/), [Log4j 2](https://logging.apache.org/log4j/2.x/), `slf4j-simple`). The library itself only includes the `slf4j-api` dependency, so it does not force a specific logging implementation on your application.

By default, no logs will be produced unless you add a logging implementation to your project's dependencies.

### Enabling Logs

To see the logs from `EmailVerifier`, you need to add a dependency on an SLF4J implementation. For example, to use a simple logger that prints to standard output, you can add the following Gradle dependency:

```groovy
testImplementation("org.slf4j:slf4j-simple:2.0.13")
```

### Configuring Log Levels

You can configure the log levels for the library's loggers to control the amount of output. The main logger categories are:

*   `io.github.mbalatsko.emailverifier.EmailVerifierDslBuilder`: Logs the configuration and building process of the `EmailVerifier`.
*   `io.github.mbalatsko.emailverifier.EmailVerifier`: Logs the overall verification process for each email.
*   `io.github.mbalatsko.emailverifier.components.checkers.*`: Loggers for individual checks (e.g., `GravatarChecker`, `SmtpChecker`).
*   `io.github.mbalatsko.emailverifier.components.core.*`: Loggers for core components like `GoogleDoHLookupBackend` and `SocketSmtpConnection`.
*   `io.github.mbalatsko.emailverifier.components.providers.*`: Loggers for data providers like `OnlineLFDomainsProvider`.

For example, with Logback, you could set the log level for the entire library to `DEBUG` by adding the following to your `logback.xml`:

```xml
<logger name="io.github.mbalatsko.emailverifier" level="DEBUG"/>
```

## 🔮 Roadmap
Planned features:

* **Typo check** suggestions
* **Multiplatform Support**
  * Support Kotlin/Native by replacing or abstracting away java.net.IDN

## ⚠️ Platform Support
* ✅ JVM
* ❌ Native/JS (pending IDN/punycode compatibility layer)


## 🙋‍♂️ Contributing
Issues, suggestions, and PRs welcome. Aim is correctness, composability, and pragmatic coverage — not full RFC simulation.

#### Built for developers who want real signal, not false validation comfort.