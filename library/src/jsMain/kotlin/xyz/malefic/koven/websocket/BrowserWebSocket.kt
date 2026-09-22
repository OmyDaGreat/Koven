package xyz.malefic.koven.websocket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

private class BrowserWebSocket(
    private val socket: WebSocket,
    private val stateValue: MutableStateFlow<WebSocketState>,
) : WebSocketTransport {
    private var textHandler: (String) -> Unit = {}
    private var closedHandler: (Int, String) -> Unit = { _, _ -> }
    private var errorHandler: (Throwable?) -> Unit = {}

    override val state: StateFlow<WebSocketState> = stateValue

    override fun send(text: String) {
        check(socket.readyState == WebSocket.OPEN) {
            "WebSocket is not open"
        }

        socket.send(text)
    }

    override fun onText(handler: (String) -> Unit) {
        textHandler = handler
    }

    override fun onClosed(handler: (code: Int, reason: String) -> Unit) {
        closedHandler = handler
    }

    override fun onError(handler: (Throwable?) -> Unit) {
        errorHandler = handler
    }

    override fun close(
        code: Int,
        reason: String,
    ) {
        stateValue.value = WebSocketState.CLOSING
        socket.close(code.toShort(), reason)
    }

    fun handleText(text: String) {
        textHandler(text)
    }

    fun handleClosed(
        code: Int,
        reason: String,
    ) {
        closedHandler(code, reason)
    }

    fun handleError(error: Throwable?) {
        errorHandler(error)
    }
}

fun connectBrowserWebSocket(url: String): WebSocketTransport {
    val socket = WebSocket(url)
    val state = MutableStateFlow(WebSocketState.CONNECTING)

    val client =
        BrowserWebSocket(
            socket = socket,
            stateValue = state,
        )

    socket.onopen = {
        state.value = WebSocketState.OPEN
    }

    socket.onmessage = { event ->
        client.handleText(event.data.toString())
    }

    socket.onerror = { _: Event ->
        state.value = WebSocketState.FAILED
        client.handleError(null)
    }

    socket.onclose = { event ->
        val closeEvent = event as org.w3c.dom.CloseEvent
        state.value = WebSocketState.CLOSED
        client.handleClosed(closeEvent.code.toInt(), closeEvent.reason)
    }

    return client
}
