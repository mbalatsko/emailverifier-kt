# EmailVerifier 📬

**EmailVerifier** is a composable, pluggable Kotlin library for validating email addresses beyond just their syntax. It's built with a clear focus: help developers **reliably assess whether a given email is real, meaningful, and worth accepting**.

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

## 🧪 Output: Validation Results

You get a detailed result for each check:

```kotlin
data class EmailValidationResult(
    val email: String,
    val syntaxCheck: CheckResult,
    val registrabilityCheck: CheckResult,
    val mxRecordCheck: CheckResult,
    val disposabilityCheck: CheckResult
) {
    fun ok(): Boolean // true if all enabled checks passed
}
```

Each check can return:
- `PASSED` ✅
- `FAILED` ❌
- `SKIPPED` ⏭️ (if not enabled or not applicable)

## 🚀 Getting Started

### 1. Add dependency (JVM only for now)

### 2. Basic usage

```kotlin
val verifier = EmailVerifier.init()

val result = verifier.verify("john.doe@example.com")

if (result.ok()) {
    println("Valid email!")
} else {
    println("Email validation failed: $result")
}
```

### 3. Custom configuration

```kotlin
val config = EmailVerifierConfig(
    enableRegistrabilityCheck = true,
    enableMxRecordCheck = true,
    enableDisposabilityCheck = false
)

val verifier = EmailVerifier.init(config)
```

## ⚙️ Powered By
* `ktor` for asynchronous HTTP
* `java.net.IDN` for domain normalization (punycode)
* Public Suffix List for registrability logic
* Community-maintained disposable email blocklists

## 🔮 Roadmap
Planned features:

* **Role-Based Username Detection** 
  * Flag addresses like `info@`, `admin@`, `support@` that are not person-specific
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