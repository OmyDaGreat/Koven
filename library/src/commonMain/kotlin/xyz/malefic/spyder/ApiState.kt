package xyz.malefic.spyder

/**
 * A state machine for async API calls.
 */
sealed interface ApiState<out T> {
    /** Initial state before the request has started. */
    data object Idle : ApiState<Nothing>

    /** The request is currently in flight. */
    data object Loading : ApiState<Nothing>

    /** The request completed successfully. */
    data class Success<T>(
        val data: T,
    ) : ApiState<T>

    /** The request failed with an [Issue]. */
    data class Error(
        val issue: Issue,
    ) : ApiState<Nothing>
}
