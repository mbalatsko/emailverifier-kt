package io.github.mbalatsko.emailverifier.components.core

/**
 * Custom exception to signal that a validation check failed due to a network or connection error.
 */
class ConnectionError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
