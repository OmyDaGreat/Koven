package xyz.malefic.spyder.server.persistence

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.malefic.spyder.core.SpyderJson
import xyz.malefic.spyder.server.SpyderServer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.getValue
import kotlin.reflect.KProperty

/**
 * A delegate for loading and saving a value, in JSON, from a file.
 */
class JsonFile<T>(
    private val fileName: String,
    private val defaultValue: T,
    private val serializer: KSerializer<T>,
) {
    private val file by lazy { File(SpyderServer.config.filesPath, fileName) }
    private val _value = AtomicReference<T?>(null)
    private val lock = ReentrantLock()

    val value: T get() {
        val current = _value.get()
        if (current != null) return current

        return lock.withLock {
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

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = value

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        newValue: T,
    ) {
        lock.withLock {
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
inline fun <reified T> jsonFile(
    fileName: String,
    defaultValue: T,
    serializer: KSerializer<T> = SpyderJson.default.serializersModule.serializer(),
) = JsonFile(fileName, defaultValue, serializer)
