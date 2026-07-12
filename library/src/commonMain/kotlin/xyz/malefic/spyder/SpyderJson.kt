package xyz.malefic.spyder

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Global JSON manager for the Spyder framework.
 */
object SpyderJson {
    private val issueSubclasses = mutableMapOf<KClass<out Issue>, KSerializer<out Issue>>()

    /**
     * Provide a custom module to overwrite the default one, for example to add custom Issue serializers.
     *
     * Usage example:
     * ```
     * val module = SerializersModule {
     *     polymorphic(Issue::class) {
     *         // Add custom issue serializers here
     *         subclass(CustomIssue::class, CustomIssue.serializer())
     *     }
     * }
     * ```
     */
    var module: SerializersModule? = null
        set(value) {
            field = value
            default = build()
        }

    init {
        registerIssue<InternalIssue>()
        registerIssue<BadRequestIssue>()
        registerIssue<UnauthorizedIssue>()
        registerIssue<RateLimitedIssue>()
    }

    var configuration: JsonBuilder.() -> Unit = {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }
        private set

    var default: Json = build()
        private set

    fun configure(block: JsonBuilder.() -> Unit) {
        configuration = block
        default = build()
    }

    /**
     * Registers a new [Issue] subclass.
     */
    fun <T : Issue> registerIssue(
        clazz: KClass<T>,
        serializer: KSerializer<T>,
    ) {
        issueSubclasses[clazz] = serializer
        default = build()
    }

    /**
     * Helper to register issues using reified types.
     */
    inline fun <reified T : Issue> registerIssue() = registerIssue(T::class, serializer<T>())

    fun build(block: JsonBuilder.() -> Unit = {}): Json =
        Json {
            configuration()
            serializersModule =
                SerializersModule {
                    polymorphic(Issue::class) {
                        issueSubclasses.forEach { (clazz, serializer) ->
                            @Suppress("UNCHECKED_CAST")
                            subclass(clazz as KClass<Issue>, serializer as KSerializer<Issue>)
                        }
                    }
                }
            module?.let { serializersModule = serializersModule.overwriteWith(it) }
            block()
        }
}
