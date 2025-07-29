package io.github.mbalatsko.emailverifier.components.checkers

import io.github.mbalatsko.emailverifier.components.core.EmailParts

/**
 * A generic interface for performing a single validation check on an email address.
 *
 * @param Output The type of the result produced by the check.
 * @param Context The type of the context required by the check.
 */
interface IChecker<out Output, in Context> {
    /**
     * Performs the validation check.
     *
     * @param email The decomposed parts of the email address to check.
     * @param context Additional context required for the check (e.g., MX records for SMTP check).
     * @return The result of the check.
     */
    suspend fun check(
        email: EmailParts,
        context: Context,
    ): Output
}

/**
 * An interface for components that can have their data refreshed at runtime.
 * This is useful for long-running applications that need to keep their data up-to-date.
 */
interface Refreshable {
    /**
     * Refreshes the data from the underlying data source.
     * This operation is expected to be thread-safe.
     */
    suspend fun refresh()
}
