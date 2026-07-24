package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

/**
 * Interface for decoding path parameters on the server.
 */
interface PathField<T> : KovenField<T> {
    context(_: Raise<Issue>)
    fun decode(params: Map<String, String>): T

    fun encodePath(value: T): Map<String, String>

    /**
     * Creates a [PathParams] object from the given [value].
     */
    fun createPath(value: T): PathParams = PathParams(encodePath(value))

    /**
     * The field names for this path parameter.
     */
    override val fields: List<String>

    /**
     * Display name for this field, derived from [fields].
     */
    val name: String get() = fields.joinToString(", ")

    /**
     * Flattens the [PathField] into its constituent fields.
     */
    override fun flatten(): List<PathField<*>> = listOf(this)

    companion object {
        val PATH_PARAM_REGEX = "\\{([^}]+)\\}".toRegex()

        operator fun invoke(name: String): PathField<String> =
            object : PathField<String> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: Map<String, String>): String =
                    ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }

                override fun encodePath(value: String): Map<String, String> = mapOf(name to value)
            }

        fun int(name: String): PathField<Int> =
            object : PathField<Int> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: Map<String, String>): Int {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer for path parameter: $name"))
                }

                override fun encodePath(value: Int): Map<String, String> = mapOf(name to value.toString())
            }

        fun long(name: String): PathField<Long> =
            object : PathField<Long> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: Map<String, String>): Long {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toLongOrNull() ?: raise(BadRequestIssue("Invalid long for path parameter: $name"))
                }

                override fun encodePath(value: Long): Map<String, String> = mapOf(name to value.toString())
            }

        fun boolean(name: String): PathField<Boolean> =
            object : PathField<Boolean> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: Map<String, String>): Boolean {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toBooleanStrictOrNull() ?: raise(BadRequestIssue("Invalid boolean for path parameter: $name"))
                }

                override fun encodePath(value: Boolean): Map<String, String> = mapOf(name to value.toString())
            }
    }
}

/**
 * A wrapper for path parameters.
 */
data class PathParams(
    val data: Map<String, String> = emptyMap(),
) : Map<String, String> by data {
    operator fun plus(other: PathParams): PathParams = PathParams(data + other.data)
}

/**
 * A path field that is optional.
 */
class OptionalPathField<T>(
    val inner: PathField<T>,
) : PathField<T?> {
    override val fields: List<String> get() = inner.fields

    override fun flatten(): List<PathField<*>> = inner.flatten()

    context(_: Raise<Issue>)
    override fun decode(params: Map<String, String>): T? {
        if (fields.none { params.containsKey(it) }) return null
        return inner.decode(params)
    }

    override fun encodePath(value: T?): Map<String, String> = value?.let { inner.encodePath(it) } ?: emptyMap()
}

/**
 * Marks a path field as optional.
 */
fun <T> PathField<T>.optional(): PathField<T?> = OptionalPathField(this)

/**
 * A path field that combines two other path fields.
 */
class PathPairField<A, B>(
    override val fieldA: PathField<A>,
    override val fieldB: PathField<B>,
) : KovenPairField<A, B>(fieldA, fieldB),
    PathField<KovenPair<A, B>> {
    override val fields: List<String> get() = super.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<PathField<*>> = super<KovenPairField>.flatten() as List<PathField<*>>

    context(_: Raise<Issue>)
    override fun decode(params: Map<String, String>): KovenPair<A, B> = KovenPair(fieldA.decode(params), fieldB.decode(params))

    override fun encodePath(value: KovenPair<A, B>): Map<String, String> = fieldA.encodePath(value.first) + fieldB.encodePath(value.second)
}

/**
 * Creates a pair of path fields as [PathPairField].
 */
infix fun <A, B> PathField<A>.and(other: PathField<B>): PathPairField<A, B> = PathPairField(this, other)

/**
 * Creates a pair of path fields as [PathPairField], optimizing for [Empty] on the left.
 */
@JvmName("andEmptyPathFieldLeft")
infix fun <B> Empty.and(other: PathField<B>): PathField<B> = other

/**
 * Creates a pair of path fields as [PathPairField], optimizing for [Empty] on the right.
 */
@JvmName("andEmptyPathFieldRight")
infix fun <A> PathField<A>.and(other: Empty): PathField<A> = this
