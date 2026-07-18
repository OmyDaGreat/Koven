package xyz.malefic.koven.server.persistence

import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.malefic.koven.serialization.ProtoBufSerializer
import xyz.malefic.koven.server.KovenServer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.getValue
import kotlin.reflect.KProperty

/**
 * A delegate for loading and saving a value, in ProtoBuf, from a file.
 */
@OptIn(ExperimentalSerializationApi::class)
class ProtoBufFile<T>(
    private val fileName: String,
    private val defaultValue: T,
    private val serializer: KSerializer<T>,
) {
    private val file by lazy { File(KovenServer.config.filesPath, fileName) }
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
                ProtoBufSerializer.default.decodeFromByteArray(serializer, file.readBytes())
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            Logger.e(e, "ProtoBufFile") { "Error loading $fileName" }
            defaultValue
        }

    private fun save(toSave: T) =
        try {
            val bytes = ProtoBufSerializer.default.encodeToByteArray(serializer, toSave)
            val tempFile = File(file.parent, "$fileName.tmp")
            file.parentFile?.mkdirs()
            tempFile.writeBytes(bytes)

            val path = file.toPath()
            val tempPath = tempFile.toPath()
            Files.move(
                tempPath,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            Logger.e(e, "ProtoBufFile") { "Error saving $fileName" }
        }
}

/**
 * A delegate for loading and saving a value, in ProtoBuf, from a file.
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> protoBufFile(
    fileName: String,
    defaultValue: T,
    serializer: KSerializer<T> = ProtoBufSerializer.default.serializersModule.serializer(),
) = ProtoBufFile(fileName, defaultValue, serializer)
