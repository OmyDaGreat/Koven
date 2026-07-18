package xyz.malefic.koven.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import xyz.malefic.koven.api.BodyFormat
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.InternalIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.error.RateLimitedIssue
import xyz.malefic.koven.error.UserIssue
import kotlin.reflect.KClass

/**
 * A wrapper for a [SerialFormat] that can produce [BodyFormat]s.
 *
 * @param Format The type of [SerialFormat] used.
 */
interface SerializationEngine<Format : SerialFormat> {
    /**
     * The default instance of the [SerialFormat].
     */
    val default: Format

    /**
     * The content type for this serialization format.
     */
    val contentType: String

    /**
     * Creates a [BodyFormat] for the given [serializer].
     */
    fun <T> createFormat(serializer: KSerializer<T>): BodyFormat<T>

    /**
     * Decodes an [Issue] from the given bytes.
     */
    fun decodeIssue(bytes: ByteArray): Issue

    /**
     * Encodes an [Issue] to a byte array.
     */
    fun encodeIssue(issue: Issue): ByteArray

    companion object {
        private val issueSubclasses = mutableMapOf<KClass<out Issue>, KSerializer<out Issue>>()

        /**
         * Global custom module for serialization. Setting this will trigger a rebuild of all serialization defaults.
         */
        var module: SerializersModule? = null
            set(value) {
                field = value
                rebuildAll()
            }

        private val listeners = mutableListOf<() -> Unit>()

        /**
         * Adds a listener to be notified when the global serialization configuration changes.
         *
         * @param listener The listener to add.
         */
        fun onChange(listener: () -> Unit) {
            listeners.add(listener)
        }

        private fun rebuildAll() {
            listeners.forEach { it() }
        }

        init {
            registerIssue<InternalIssue>()
            registerIssue<BadRequestIssue>()
            registerIssue<AuthIssue.Unauthorized>()
            registerIssue<AuthIssue.InvalidCredentials>()
            registerIssue<AuthIssue.InvalidToken>()
            registerIssue<AuthIssue.AccountLocked>()
            registerIssue<AuthIssue.MissingToken>()
            registerIssue<UserIssue.AlreadyExists>()
            registerIssue<UserIssue.NotFound>()
            registerIssue<UserIssue.InvalidUser>()
            registerIssue<RateLimitedIssue>()
        }

        /**
         * Registers a new [Issue] subclass globally.
         */
        fun <T : Issue> registerIssue(
            clazz: KClass<T>,
            serializer: KSerializer<T>,
        ) {
            issueSubclasses[clazz] = serializer
            rebuildAll()
        }

        /**
         * Helper to register issues using reified types.
         */
        inline fun <reified T : Issue> registerIssue() = registerIssue(T::class, serializer<T>())

        /**
         * Builds a [SerializersModule] containing all registered issue subclasses.
         */
        fun buildModule(): SerializersModule =
            SerializersModule {
                polymorphic(Issue::class) {
                    issueSubclasses.forEach { (clazz, serializer) ->
                        @Suppress("UNCHECKED_CAST")
                        subclass(clazz as KClass<Issue>, serializer as KSerializer<Issue>)
                    }
                }
            }.let { base ->
                module?.let { base.overwriteWith(it) } ?: base
            }
    }
}
