package xyz.malefic.koven.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBufBuilder
import xyz.malefic.koven.api.BodyFormat
import xyz.malefic.koven.api.ProtoBufFormat
import xyz.malefic.koven.error.Issue

/**
 * Global [ProtoBuf] manager for the Koven framework.
 */
@OptIn(ExperimentalSerializationApi::class)
object ProtoBufSerializer : SerializationEngine<ProtoBuf> {
    init {
        SerializationEngine.onChange { default = build() }
    }

    /**
     * The custom configuration for [ProtoBuf].
     */
    var configuration: ProtoBufBuilder.() -> Unit = {
        encodeDefaults = true
    }
        private set

    override var default: ProtoBuf = build()
        private set

    override val contentType = "application/x-protobuf"

    /**
     * Configures the [ProtoBuf] instance.
     */
    fun configure(block: ProtoBufBuilder.() -> Unit) {
        configuration = block
        default = build()
    }

    override fun <T> createFormat(serializer: KSerializer<T>): BodyFormat<T> = ProtoBufFormat(serializer)

    override fun decodeIssue(bytes: ByteArray): Issue = default.decodeFromByteArray(Issue.serializer(), bytes)

    override fun encodeIssue(issue: Issue): ByteArray = default.encodeToByteArray(Issue.serializer(), issue)

    /**
     * Builds a new [ProtoBuf] instance using the current configuration and global module.
     */
    fun build(block: ProtoBufBuilder.() -> Unit = {}): ProtoBuf =
        ProtoBuf {
            configuration()
            serializersModule = SerializationEngine.buildModule()
            block()
        }
}
