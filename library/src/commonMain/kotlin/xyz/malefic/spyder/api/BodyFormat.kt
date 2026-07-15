package xyz.malefic.spyder.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.malefic.spyder.serialization.Serializer
import xyz.malefic.spyder.serialization.SpyderJson
import xyz.malefic.spyder.serialization.SpyderProtoBuf

/**
 * Defines how a type [T] is transformed for transmission.
 *
 * @param T The type of the body to encode/decode.
 */
interface BodyFormat<T> {
    /**
     * The serialization engine that produced this format, if any.
     */
    val serialization: Serializer<*>? get() = null

    /**
     * The content type of the body.
     */
    val contentType: String

    /**
     * Encodes the value into a byte array for transmission.
     */
    fun encode(value: T): ByteArray

    /**
     * Decodes the byte array into the type [T].
     *
     * @param bytes The byte array to decode.
     * @param contentType The content type of the response, if available.
     */
    fun decode(
        bytes: ByteArray,
        contentType: String,
    ): T
}

/**
 * A [BodyFormat] that uses [SpyderJson] to encode and decode values.
 *
 * @param serializer The [KSerializer] to use for encoding and decoding.
 *
 * @param T The type of the body to encode/decode.
 */
class JsonFormat<T>(
    private val serializer: KSerializer<T>,
) : BodyFormat<T> {
    override val serialization = SpyderJson
    override val contentType = "application/json"

    override fun encode(value: T): ByteArray = SpyderJson.default.encodeToString(serializer, value).encodeToByteArray()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): T = SpyderJson.default.decodeFromString(serializer, bytes.decodeToString())

    companion object {
        /**
         * Creates a [JsonFormat] for the given type [T].
         */
        inline fun <reified T> create(): JsonFormat<T> = JsonFormat(serializer<T>())
    }
}

/**
 * A [BodyFormat] that uses [SpyderProtoBuf] to encode and decode values.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ProtoBufFormat<T>(
    private val serializer: KSerializer<T>,
) : BodyFormat<T> {
    override val serialization = SpyderProtoBuf
    override val contentType = "application/x-protobuf"

    override fun encode(value: T): ByteArray = SpyderProtoBuf.default.encodeToByteArray(serializer, value)

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): T = SpyderProtoBuf.default.decodeFromByteArray(serializer, bytes)

    companion object {
        /**
         * Creates a [ProtoBufFormat] for the given type [T].
         */
        inline fun <reified T> create(): ProtoBufFormat<T> = ProtoBufFormat(serializer<T>())
    }
}

/**
 * A [BodyFormat] for [Unit] bodies.
 */
object UnitFormat : BodyFormat<Unit> {
    override val contentType = "application/octet-stream"

    override fun encode(value: Unit): ByteArray = byteArrayOf()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): Unit = Unit
}

/**
 * A [BodyFormat] for plain text bodies.
 */
object PlainTextFormat : BodyFormat<String> {
    override val contentType = "text/plain"

    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): String = bytes.decodeToString()
}

/**
 * A [BodyFormat] for HTML bodies.
 */
object HtmlFormat : BodyFormat<String> {
    override val contentType = "text/html"

    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): String = bytes.decodeToString()
}

/**
 * A [BodyFormat] for XML bodies.
 */
object XmlFormat : BodyFormat<String> {
    override val contentType = "application/xml"

    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): String = bytes.decodeToString()
}

/**
 * A [BodyFormat] for binary bodies.
 */
object BinaryFormat : BodyFormat<ByteArray> {
    override val contentType = "application/octet-stream"

    override fun encode(value: ByteArray): ByteArray = value

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): ByteArray = bytes
}

/**
 * A [BodyFormat] for application/x-www-form-urlencoded bodies.
 */
object FormUrlEncodedFormat : BodyFormat<Map<String, List<String>>> {
    override val contentType = "application/x-www-form-urlencoded"

    override fun encode(value: Map<String, List<String>>): ByteArray =
        value.entries
            .flatMap { (k, vs) -> vs.map { v -> "${k.urlEncode()}=${v.urlEncode()}" } }
            .joinToString("&")
            .encodeToByteArray()

    override fun decode(
        bytes: ByteArray,
        contentType: String,
    ): Map<String, List<String>> {
        val decodedString = bytes.decodeToString()
        if (decodedString.isEmpty()) return emptyMap()

        return decodedString
            .split("&")
            .filter { it.isNotEmpty() }
            .map { pair ->
                val parts = pair.split("=", limit = 2)
                val key = parts[0].urlDecode()
                val value = parts.getOrNull(1)?.urlDecode() ?: ""
                key to value
            }.groupBy({ it.first }, { it.second })
    }

    private fun String.urlEncode(): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._*"
        return buildString {
            for (char in this@urlEncode) {
                when (char) {
                    in allowed -> {
                        append(char)
                    }

                    ' ' -> {
                        append('+')
                    }

                    else -> {
                        char.toString().encodeToByteArray().forEach { byte ->
                            append('%')
                            append(
                                byte
                                    .toInt()
                                    .and(0xFF)
                                    .toString(16)
                                    .uppercase()
                                    .padStart(2, '0'),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun String.urlDecode(): String {
        val bytes = ByteArray(this.length)
        var j = 0
        var i = 0
        while (i < this.length) {
            when (val c = this[i]) {
                '+' -> {
                    bytes[j++] = ' '.code.toByte()
                }

                '%' -> {
                    if (i + 2 < this.length) {
                        val hex = this.substring(i + 1, i + 3)
                        bytes[j++] = hex.toInt(16).toByte()
                        i += 2
                    } else {
                        bytes[j++] = '%'.code.toByte()
                    }
                }

                else -> {
                    bytes[j++] = c.code.toByte()
                }
            }
            i++
        }
        return bytes.decodeToString(0, j)
    }
}
