package xyz.malefic.spyder.server

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.malefic.spyder.SpyderJson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.getValue
import kotlin.reflect.KProperty

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
class FileDelegate<T>(
    private val fileName: String,
    private val defaultValue: T,
    private val serializer: KSerializer<T>,
) {
    private val file by lazy { File(SpyderServer.config.assetsPath, fileName) }
    private val _value = AtomicReference<T?>(null)
    private val lock = Any()

    val value: T get() {
        val current = _value.get()
        if (current != null) return current

        return synchronized(lock) {
            val doubleCheck = _value.get()
            if (doubleCheck != null) {
                doubleCheck
            } else {
                val loaded = load()
                _value.set(loaded)
                loaded
            }
        }
    }

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        newValue: T,
    ) {
        synchronized(lock) {
            _value.set(newValue)
            save(newValue)
        }
    }

    private fun load(): T =
        try {
            if (file.exists()) {
                SpyderJson.default.decodeFromString(serializer, file.readText())
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            Logger.e(e, "FileDelegate") { "Error loading $fileName" }
            defaultValue
        }

    private fun save(toSave: T) =
        try {
            val json = SpyderJson.default.encodeToString(serializer, toSave)
            val tempFile = File(file.parent, "$fileName.tmp")
            file.parentFile?.mkdirs()
            tempFile.writeText(json)

            val path = file.toPath()
            val tempPath = tempFile.toPath()
            Files.move(
                tempPath,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            Logger.e(e, "FileDelegate") { "Error saving $fileName" }
        }
}

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
inline fun <reified T> file(
    fileName: String,
    defaultValue: T,
    serializer: KSerializer<T> = SpyderJson.default.serializersModule.serializer(),
) = FileDelegate(fileName, defaultValue, serializer)
