package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * A wrapper for HTTP headers that maintains insertion order and supports multiple values.
 * Header fields are treated case-insensitively.
 */
class Headers private constructor(
    private val data: Map<String, List<String>>,
    marker: Unit,
) : Map<String, List<String>> by data {
    constructor(map: Map<String, List<String>> = emptyMap()) : this(
        if (map.isEmpty()) {
            emptyMap()
        } else {
            val result = LinkedHashMap<String, MutableList<String>>(map.size)
            map.forEach { (k, v) ->
                result.getOrPut(k.lowercase()) { mutableListOf() }.addAll(v)
            }
            result.mapValues { it.value.toList() }
        },
        Unit,
    )

    override fun containsKey(key: String): Boolean = data.containsKey(key.lowercase())

    override operator fun get(key: String): List<String>? = data[key.lowercase()]

    /**
     * Gets all values for a given [HeaderField].
     */
    operator fun <T> get(field: HeaderField<T>): List<String>? = get(field.field)

    /**
     * Gets the first value for a given [HeaderField].
     *
     * @param field The [HeaderField].
     */
    fun <T> getFirst(field: HeaderField<T>): String? = get(field)?.firstOrNull()

    /**
     * Gets the first value for a given header field.
     *
     * @param field The field name.
     */
    fun getFirst(field: String): String? = get(field)?.firstOrNull()

    /**
     * Combines two [Headers] instances into a single one.
     */
    operator fun plus(other: Headers): Headers {
        if (this.isEmpty()) return other
        if (other.isEmpty()) return this
        val newMap = data.toMutableMap()
        other.data.forEach { (k, v) ->
            newMap[k] = (newMap[k] ?: emptyList()) + v
        }
        return Headers(newMap, Unit)
    }

    companion object {
        fun build(block: Builder.() -> Unit): Headers = Builder().apply(block).build()

        /**
         * Converts a collection of pairs into [Headers].
         */
        fun fromPairs(pairs: Iterable<Pair<String, String?>>): Headers =
            build {
                pairs.forEach { (k, v) -> v?.let { append(k, v) } }
            }
    }

    class Builder {
        private val map = LinkedHashMap<String, MutableList<String>>()

        @IgnorableReturnValue
        fun append(
            name: String,
            value: String,
        ) = apply {
            map.getOrPut(name.lowercase()) { mutableListOf() }.add(value)
        }

        operator fun set(
            name: String,
            value: String,
        ) {
            map[name.lowercase()] = mutableListOf(value)
        }

        @IgnorableReturnValue
        fun add(headers: Headers) =
            apply {
                headers.forEach { (k, v) -> v.forEach { append(k, it) } }
            }

        fun build(): Headers = Headers(data = map.mapValues { it.value.toList() }, Unit)
    }
}

/**
 * Interface for header fields.
 */
interface HeaderField<T> : KovenField<T> {
    val field: String

    /**
     * The fields for this header field.
     */
    override val fields: List<String>

    context(_: Raise<Issue>)
    fun decode(headers: Headers): T

    fun encodeHeaders(value: T): Map<String, List<String>>

    /**
     * Creates a [Headers] object from the given [value].
     */
    fun createHeaders(value: T): Headers = Headers(encodeHeaders(value))

    /**
     * Flattens the [HeaderField] into its constituent fields.
     */
    override fun flatten(): List<HeaderField<*>> = listOf(this)

    companion object {
        operator fun invoke(name: String): HeaderField<String> =
            object : HeaderField<String> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): String =
                    ensureNotNull(headers.getFirst(name)) { BadRequestIssue("Missing required header: $name") }

                override fun encodeHeaders(value: String): Map<String, List<String>> = mapOf(field to listOf(value))
            }

        fun int(name: String): HeaderField<Int> =
            object : HeaderField<Int> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): Int {
                    val value = ensureNotNull(headers.getFirst(name)) { BadRequestIssue("Missing required header: $name") }
                    return value.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer for header: $name"))
                }

                override fun encodeHeaders(value: Int): Map<String, List<String>> = mapOf(field to listOf(value.toString()))
            }

        fun long(name: String): HeaderField<Long> =
            object : HeaderField<Long> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): Long {
                    val value = ensureNotNull(headers.getFirst(name)) { BadRequestIssue("Missing required header: $name") }
                    return value.toLongOrNull() ?: raise(BadRequestIssue("Invalid long for header: $name"))
                }

                override fun encodeHeaders(value: Long): Map<String, List<String>> = mapOf(field to listOf(value.toString()))
            }

        fun boolean(name: String): HeaderField<Boolean> =
            object : HeaderField<Boolean> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): Boolean {
                    val value = ensureNotNull(headers.getFirst(name)) { BadRequestIssue("Missing required header: $name") }
                    return value.toBooleanStrictOrNull() ?: raise(BadRequestIssue("Invalid boolean for header: $name"))
                }

                override fun encodeHeaders(value: Boolean): Map<String, List<String>> = mapOf(field to listOf(value.toString()))
            }

        fun list(name: String): HeaderField<List<String>> =
            object : HeaderField<List<String>> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): List<String> =
                    ensureNotNull(headers[name]) { BadRequestIssue("Missing required header: $name") }

                override fun encodeHeaders(value: List<String>): Map<String, List<String>> = mapOf(field to value)
            }
    }
}

/**
 * A header field that decodes all headers into a [Headers] object.
 */
object HeadersField : HeaderField<Headers> {
    override val field: String = "*"
    override val fields: List<String> = emptyList()

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): Headers = headers

    override fun encodeHeaders(value: Headers): Map<String, List<String>> = value

    override fun createHeaders(value: Headers): Headers = value

    override fun flatten(): List<Nothing> = emptyList()
}

/**
 * A header field for HTTP redirection.
 */
object Redirect : HeaderField<String> {
    override val field: String = "Location"
    override val fields: List<String> = listOf(field)

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): String = ensureNotNull(headers.getFirst(field)) { BadRequestIssue("Missing Location header") }

    override fun encodeHeaders(value: String): Map<String, List<String>> = mapOf(field to listOf(value))

    override fun createHeaders(value: String): Headers = Headers(encodeHeaders(value))
}

/**
 * A header field that is optional.
 */
class OptionalHeaderField<T>(
    val inner: HeaderField<T>,
) : HeaderField<T?> {
    override val field: String get() = inner.field
    override val fields: List<String> get() = inner.fields

    override fun flatten(): List<HeaderField<*>> = inner.flatten()

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): T? {
        if (fields.none { headers.containsKey(it) }) return null
        return inner.decode(headers)
    }

    override fun encodeHeaders(value: T?): Map<String, List<String>> = value?.let { inner.encodeHeaders(it) } ?: emptyMap()
}

/**
 * Marks a header field as optional.
 */
fun <T> HeaderField<T>.optional(): HeaderField<T?> = OptionalHeaderField(this)

/**
 * A header field that combines two other header fields. Used to decode [KovenPair].
 */
class HeaderPairField<A, B>(
    override val fieldA: HeaderField<A>,
    override val fieldB: HeaderField<B>,
) : KovenPairField<A, B>(fieldA, fieldB),
    HeaderField<KovenPair<A, B>> {
    override val field: String = "${fieldA.field}, ${fieldB.field}"
    override val fields: List<String> get() = super.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<HeaderField<*>> = super<KovenPairField>.flatten() as List<HeaderField<*>>

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): KovenPair<A, B> = KovenPair(fieldA.decode(headers), fieldB.decode(headers))

    override fun encodeHeaders(value: KovenPair<A, B>): Map<String, List<String>> =
        fieldA.encodeHeaders(value.first) + fieldB.encodeHeaders(value.second)
}

/**
 * Creates a pair of header fields as [HeaderPairField].
 */
infix fun <A, B> HeaderField<A>.and(other: HeaderField<B>): HeaderPairField<A, B> = HeaderPairField(this, other)
