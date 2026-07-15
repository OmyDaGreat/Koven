package xyz.malefic.spyder.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import xyz.malefic.spyder.api.BodyFormat
import xyz.malefic.spyder.api.JsonFormat
import xyz.malefic.spyder.error.Issue

/**
 * Global [Json] manager for the Spyder framework.
 */
object JsonSerializer : SerializationEngine<Json> {
    init {
        SerializationEngine.onChange { default = build() }
    }

    /**
     * The custom configuration for [Json].
     */
    var configuration: JsonBuilder.() -> Unit = {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }
        private set

    override var default: Json = build()
        private set

    override val contentType = "application/json"

    /**
     * Configures the [Json] instance.
     */
    fun configure(block: JsonBuilder.() -> Unit) {
        configuration = block
        default = build()
    }

    override fun <T> createFormat(serializer: KSerializer<T>): BodyFormat<T> = JsonFormat(serializer)

    override fun decodeIssue(bytes: ByteArray): Issue = default.decodeFromString(Issue.serializer(), bytes.decodeToString())

    override fun encodeIssue(issue: Issue): ByteArray = default.encodeToString(Issue.serializer(), issue).encodeToByteArray()

    /**
     * Builds a new [Json] instance using the current configuration and global module.
     */
    fun build(block: JsonBuilder.() -> Unit = {}): Json =
        Json {
            configuration()
            serializersModule = SerializationEngine.buildModule()
            block()
        }
}
