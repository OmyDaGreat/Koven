package xyz.malefic.koven.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A property delegate that can be locked to prevent further modifications.
 */
class Lockable<T>(
    initialValue: T,
    private val isLocked: () -> Boolean,
) : ReadWriteProperty<Any?, T> {
    private var value = initialValue

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = value

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        if (isLocked()) {
            error("Cannot change ${property.name} after configuration is locked.")
        }
        this.value = value
    }

    companion object {
        /**
         * A global lock for configuration changes.
         */
        @PublishedApi
        internal var locked = false
    }
}

/**
 * Creates a [Lockable] property delegate with the given initial value and a lock condition.
 */
fun <T> lock(
    value: T,
    isLocked: () -> Boolean,
) = Lockable(value, isLocked)

/**
 * Creates a [Lockable] property delegate with the given initial value and the global lock variable.
 */
internal fun <T> lock(value: T) = Lockable(value) { Lockable.locked }
