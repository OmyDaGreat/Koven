package xyz.malefic.koven.websocket

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

class TypedWebSocket<Send, Receive>(
    private val transport: WebSocketTransport,
    private val sendSerializer: SerializationStrategy<Send>,
    private val receiveDeserializer: DeserializationStrategy<Receive>,
    private val json: Json = Json,
) {
    fun send(value: Send) {
        transport.send(json.encodeToString(sendSerializer, value))
    }

    fun onMessage(handler: (Receive) -> Unit) {
        transport.onText { text ->
            handler(json.decodeFromString(receiveDeserializer, text))
        }
    }

    fun close(
        code: Int = 1000,
        reason: String = "",
    ) {
        transport.close(code, reason)
    }
}

fun <Send, Receive> typedWebSocket(
    transport: WebSocketTransport,
    sendSerializer: SerializationStrategy<Send>,
    receiveDeserializer: DeserializationStrategy<Receive>,
    json: Json = Json,
): TypedWebSocket<Send, Receive> =
    TypedWebSocket(
        transport = transport,
        sendSerializer = sendSerializer,
        receiveDeserializer = receiveDeserializer,
        json = json,
    )
