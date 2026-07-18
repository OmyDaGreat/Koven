package xyz.malefic.spyder.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import arrow.core.Either
import xyz.malefic.spyder.core.ApiState
import xyz.malefic.spyder.error.Issue

/**
 * Converts a suspend request returning Either<Issue, T> into a Composable-supporting [ApiState].
 *
 * Usage example:
 * ```
 * val apiState by produceApiState(userId) {
 *     UserContract.call(UserRequest(userId), NoHeader)
 * }
 * ```
 *
 * @param keys The keys to use for the produceState.
 * @param block The suspend request to execute.
 */
@Composable
fun <T> produceApiState(
    vararg keys: Any?,
    block: suspend () -> Either<Issue, T>,
) = produceState<ApiState<T>>(ApiState.Idle, *keys) {
    value = ApiState.Loading
    block().fold(
        { value = ApiState.Error(it) },
        { value = ApiState.Success(it) },
    )
}
