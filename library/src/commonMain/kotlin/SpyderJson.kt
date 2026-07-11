package xyz.malefic.spyder

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object SpyderJson {
    private val issueSubclasses = mutableMapOf<KClass<out Issue>, KSerializer<out Issue>>()

    init {
        registerIssue<InternalIssue>()
        registerIssue<BadRequestIssue>()
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
    fun <T : Issue> registerIssue( // TODO: Find a way to not have to register issues
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
            block()
        }
}
