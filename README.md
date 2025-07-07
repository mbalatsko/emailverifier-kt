# EmailVerifier 📬

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
- Rejects emails like `user@something.invalid`, allows `user@example.co.uk`

### 3. **MX Record Lookup**
Ensures the domain is actually configured to receive emails:
- Queries DNS-over-HTTPS (DoH) via Google
- Fails gracefully if no MX records found

### 4. **Disposable Email Detection**
Filters out **temporary/disposable** email domains:
- Uses curated lists from [disposable-email-domains](https://github.com/disposable/disposable-email-domains)
- Detects domains like `mailinator.com`, `tempmail.org`, etc.

### 5. **Gravatar Existence Check**

Detects whether an email has an associated **Gravatar**:
- Computes MD5 hash of the email
- Returns `PASSED` result if a custom avatar exists

### 6. **Free Email Provider Detection**
Checks whether the email domain belongs to a known free‐email provider (e.g. `gmail.com`, `yahoo.com`) 
using a curated list of popular services.
- Returns `PASSED` result if email hostname is not a known free‐email provider

List used: [Github gist](https://gist.github.com/okutbay/5b4974b70673dfdcc21c517632c1f984) by @okutbay 

### 7. **Role-Based Username Detection**
Detects generic or departmental username (e.g. `info@`, `admin@`, `support@`) by checking against a curated list of common role-based usernames.

List used: https://github.com/mbalatsko/role-based-email-addresses-list (original repo: https://github.com/mixmaxhq/role-based-email-addresses)

### 8. **Offline Mode**
For environments without internet access, `EmailVerifier` can run in a fully **offline** mode. When enabled, it uses bundled 
data for checks that support it (Syntax, Registrability, Disposability, Free Email, and Role-Based Username) and automatically 
disables checks that require network access (MX Record, Gravatar). 

You can also configure **offline mode** for each check **individually**.

The bundled data is automatically updated **daily** via a GitHub Actions workflow.

## 🧪 Output: Validation Results

You get a detailed result for each check:

```kotlin
data class EmailValidationResult(
    val email: String,
    val syntaxCheck: CheckResult,
    val registrabilityCheck: CheckResult,
    val mxRecordCheck: CheckResult,
    val disposabilityCheck: CheckResult,
    val gravatarCheck: CheckResult,
    val freeCheck: CheckResult,
    val roleBasedUsernameCheck: CheckResult
) {
  /**
   * Returns true if all strong indicator checks either passed or were skipped.
   * Strong indicator checks: syntax, registrability, mx record presence, disposability
   * Note: mx record presence might return ERRORED, which is not validated
   */
    fun ok(): Boolean
}
```

Each check can return:
- `PASSED` ✅
- `FAILED` ❌
- `ERRORED` ⚠️ (if an unexpected error occurred during the check)
- `SKIPPED` ⏭️ (if not enabled or not applicable)

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

if (result.ok()) {
    println("Valid email!")
} else {
    println("Email validation failed: $result")
}
```

### 3. Custom configuration

```kotlin
val verifier = emailVerifier {
    // All checks are enabled by default unless specified otherwise.

    registrability {
        enabled = true // Explicitly enable (default is true)
        pslUrl  = "https://my.custom.domain/public_suffix_list.dat" // Custom PSL URL
        offline = false // Use online data for this check
    }
    mxRecord {
        enabled = false // Disable MX record checks
        // dohServerEndpoint = "https://my.custom.doh/dns-query" // Custom DoH endpoint if enabled
    }
    disposability {
        enabled = true // Explicitly enable (default is true)
        domainsListUrl = "https://my.custom.domain/disposable_domains.txt" // Custom disposable domains list
    }
    gravatar {
        enabled = true // Explicitly enable (default is true)
    }
    free {
        enabled = false // Disable free email provider checks
        // domainsListUrl = "https://my.custom.domain/free_emails.txt" // Custom free emails list if enabled
    }
    roleBasedUsername {
        enabled = true // Explicitly enable (default is true)
        usernamesListUrl = "https://my.custom.domain/role_based_usernames.txt" // Custom role-based usernames list
    }
    // You can also provide a custom HttpClient for all network operations:
    // httpClient = customHttpClient
}
```

### 4. Offline Mode

To enable offline mode for all checks, set the `allOffline` property to `true`. This will use bundled data for all 
supported checks and disable network-dependent checks.

```kotlin
val verifier = emailVerifier {
    allOffline = true
}

val result = verifier.verify("mbalatsko@gmail.com")
// result.mxRecordCheck will be SKIPPED
// result.gravatarCheck will be SKIPPED
```

You can also configure offline mode for each check individually.

```kotlin
val verifier = emailVerifier {
    registrability {
        offline = true // Use offline data for this check
    }
    disposability {
        offline = true
    }
    // MX and Gravatar checks will still run unless disabled
}
```

### 5. Advanced Configuration: Custom HttpClient

For more advanced use cases, such as configuring retries for network requests, you can pass a custom-configured `HttpClient` to the `EmailVerifier`. This gives you full control over the network layer.

Here's an example of how to configure an `HttpClient` with automatic retries using Ktor's `HttpRequestRetry` plugin:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*

// Configure an HttpClient with a retry policy
val customHttpClient = HttpClient(CIO) {
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }
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

## ⚙️ Powered By
* `ktor` for asynchronous HTTP
* `java.net.IDN` for domain normalization (punycode)
* Public Suffix List for registrability logic
* Community-maintained disposable email blocklists

## 🔮 Roadmap
Planned features:

* **Typo check** suggestions
* **SMTP Probing**
  * Connect to the target mail server and verify deliverability via the `RCPT TO` SMTP command (without sending email)
* **Multiplatform Support**
  * Support Kotlin/Native by replacing or abstracting away java.net.IDN

## ⚠️ Platform Support
* ✅ JVM
* ❌ Native/JS (pending IDN/punycode compatibility layer)


## 🙋‍♂️ Contributing
Issues, suggestions, and PRs welcome. Aim is correctness, composability, and pragmatic coverage — not full RFC simulation.

#### Built for developers who want real signal, not false validation comfort.