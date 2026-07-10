package xyz.malefic.spyder.server

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.malefic.spyder.SpyderJson
import java.io.File
import kotlin.getValue
import kotlin.reflect.KProperty

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
class FileDelegate<T>(
    private val baseDir: String,
    private val fileName: String,
    private val defaultValue: T,
    private val serializer: KSerializer<T>,
) {
    private val file by lazy { File(baseDir, fileName) }

    private var _value: T? = null
    val value: T get() {
        if (_value == null) _value = load()
        return _value!!
    }

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = value

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        newValue: T,
    ) {
        _value = newValue
        save()
    }

    private fun load(): T =
        if (file.exists()) {
            try {
                SpyderJson.default.decodeFromString(serializer, file.readText())
            } catch (e: Exception) {
                Logger.e(e, "FileDelegate") { "Error loading $fileName" }
                defaultValue
            }
        } else {
            defaultValue
        }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(SpyderJson.default.encodeToString(serializer, value))
    }
}

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
inline fun <reified T> file(
    fileName: String,
    defaultValue: T,
    baseDir: String = "assets",
) = FileDelegate(baseDir, fileName, defaultValue, SpyderJson.default.serializersModule.serializer<T>())

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
inline fun <reified T> SpyderConfig.file(
    fileName: String,
    defaultValue: T,
) = file(fileName, defaultValue, assetsPath)

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
context(config: SpyderConfig)
inline fun <reified T> file(
    fileName: String,
    defaultValue: T,
) = file(fileName, defaultValue, config.assetsPath)
