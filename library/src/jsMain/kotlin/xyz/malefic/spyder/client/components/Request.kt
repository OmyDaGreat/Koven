package xyz.malefic.spyder.client.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import xyz.malefic.spyder.core.ApiState
import xyz.malefic.spyder.error.AuthIssue
import xyz.malefic.spyder.error.Issue

/**
 * A basic composable for rendering API states, serving to wrap your own UI logic within [successContent]. The recommendation for its usage would be to wrap it with reasonable defaults according to your own application, as detailed in each parameter.
 *
 * @param state The API state to render. This value can be obtained from a [xyz.malefic.spyder.client.produceApiState] call.
 * @param onUnauthorized A callback to be invoked when an unauthorized error is encountered. This is typically used to redirect the user to a login page.
 * @param loadingContent A composable to render while the API request is loading. This is typically something simple like a Spinner.
 * @param errorContent A composable to render when an error is encountered.
 * @param successContent A composable to render when the API request is successful.
 */
@Composable
fun <T> Request(
    state: ApiState<T>,
    onUnauthorized: () -> Unit,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable (Issue) -> Unit,
    successContent: @Composable (T) -> Unit,
) {
    when (state) {
        is ApiState.Idle -> {}

        is ApiState.Loading -> {
            loadingContent()
        }

        is ApiState.Error -> {
            when (state.issue) {
                is AuthIssue -> LaunchedEffect(state) { onUnauthorized() }
                else -> errorContent(state.issue)
            }
        }

        is ApiState.Success -> {
            successContent(state.data)
        }
    }
}
