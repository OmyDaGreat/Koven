package xyz.malefic.koven.websocket

import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * A registry for managing WebSocket connections in different rooms.
 *
 * Since this registry implementation is managed completely through memory, it is not suitable for use in a distributed environment.
 *
 * @param Room The type representing a room.
 * @param Event The type representing an event to be broadcasted.
 * @property encode A function to encode an event into a string message.
 */
class WebSocketRegistry<in Room : Any, in Event>(
    private val encode: (Event) -> String,
) {
    private val rooms = ConcurrentHashMap<Room, MutableSet<Websocket>>()

    fun join(
        room: Room,
        socket: Websocket,
    ) {
        rooms.computeIfAbsent(room) { ConcurrentHashMap.newKeySet() }.add(socket)
    }

    fun leave(
        room: Room,
        socket: Websocket,
    ) {
        rooms[room]?.remove(socket)
        rooms.remove(room, emptySet())
    }

    fun broadcast(
        room: Room,
        event: Event,
    ) {
        val message = WsMessage(encode(event))

        rooms[room]
            ?.toList()
            ?.forEach { socket ->
                runCatching {
                    socket.send(message)
                }.onFailure {
                    leave(room, socket)
                }
            }
    }

    fun closeRoom(room: Room) {
        rooms.remove(room)?.forEach { socket ->
            runCatching { socket.close() }
        }
    }
}
