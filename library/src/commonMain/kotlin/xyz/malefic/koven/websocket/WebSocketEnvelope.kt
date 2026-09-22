package xyz.malefic.koven.websocket

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED,
    FAILED,
}

interface WebSocketTransport {
    val state: StateFlow<WebSocketState>

    fun send(text: String)

    fun onText(handler: (String) -> Unit)

    fun onClosed(handler: (code: Int, reason: String) -> Unit)

    fun onError(handler: (Throwable?) -> Unit)

    fun close(
        code: Int = 1000,
        reason: String = "",
    )
}
