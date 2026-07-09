package xyz.malefic.spyder

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

object SpyderJson {
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

    fun build(block: JsonBuilder.() -> Unit = {}): Json =
        Json {
            configuration()
            block()
        }
}
