package io.github.mbalatsko.emailverifier.components.core

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
