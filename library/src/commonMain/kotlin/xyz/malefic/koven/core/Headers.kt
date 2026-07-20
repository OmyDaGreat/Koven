package xyz.malefic.koven.core

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * A wrapper for HTTP headers that maintains insertion order and supports multiple values.
 * Header fields are treated case-insensitively.
 */
class Headers private constructor(
    private val data: Map<String, List<String>>,
    marker: Unit,
) : Map<String, List<String>> by data,
    HeaderProvider {
    constructor(map: Map<String, List<String>> = emptyMap()) :
        this(data = map.entries.groupBy({ it.key.lowercase() }, { it.value }).mapValues { (_, values) -> values.flatten() }, Unit)

    override fun containsKey(key: String): Boolean = data.containsKey(key.lowercase())

    override operator fun get(key: String): List<String>? = data[key.lowercase()]

    override fun Builder.provide() {
        forEach { (k, v) -> v.forEach { append(k, it) } }
    }

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
     *
     * @param other The [Headers] to combine with.
     *
     * @return A new [Headers] with the combined data.
     */
    operator fun plus(other: HeaderProvider): Headers =
        build {
            add(this@Headers)
            add(other)
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
        fun add(provider: HeaderProvider) =
            apply {
                with(provider) { provide() }
            }

        fun build(): Headers = Headers(data = map.mapValues { it.value.toList() }, Unit)
    }
}

/**
 * Interface for anything that can contribute headers to a [Headers.Builder].
 */
interface HeaderProvider {
    fun Headers.Builder.provide()
}

/**
 * Interface for header fields.
 */
interface HeaderField<out T> {
    val field: String

    context(_: Raise<Issue>)
    fun decode(headers: Headers): T

    /**
     * Flattens the [HeaderField] into its constituent fields.
     */
    fun flatten(): List<HeaderField<*>> = listOf(this)

    companion object {
        operator fun invoke(name: String): HeaderField<String> =
            object : HeaderField<String> {
                override val field: String = name

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): String =
                    ensureNotNull(headers.getFirst(name)) { BadRequestIssue("Missing required header: $name") }
            }
    }
}

/**
 * Represents "No Header" for contracts like [xyz.malefic.koven.api.HealthContract] and [xyz.malefic.koven.api.PingContract].
 */
object NoHeader : HeaderProvider, HeaderField<NoHeader> {
    override val field: String = ""

    override fun Headers.Builder.provide() {}

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): NoHeader = this

    override fun flatten(): List<HeaderField<*>> = emptyList()
}

/**
 * A header field that decodes all headers into a [Headers] object.
 */
object HeadersField : HeaderField<Headers> {
    override val field: String = "*"

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): Headers = headers

    override fun flatten(): List<HeaderField<*>> = emptyList()
}

/**
 * A header for HTTP redirection.
 */
class Redirect(
    val location: String,
) : Header {
    override val field: String = Companion.field
    override val values: List<String> = listOf(location)

    companion object : HeaderField<Redirect> {
        override val field: String = "Location"

        context(_: Raise<Issue>)
        override fun decode(headers: Headers): Redirect =
            Redirect(ensureNotNull(headers.getFirst(field)) { BadRequestIssue("Missing Location header") })
    }
}

/**
 * Interface for header implementations. The companion object of this interface is used to create a [HeaderField]. You can check out an example with [xyz.malefic.koven.feature.auth.BearerAuth].
 */
interface Header : HeaderProvider {
    val field: String
    val values: List<String>

    override fun Headers.Builder.provide() {
        values.forEach { append(field, it) }
    }
}

/**
 * A pair of [HeaderProvider]s. Used as a convenience wrapper to combine multiple headers into a single header for [xyz.malefic.koven.api.ApiContract]. These can also be composed with each other to create more complex headers.
 */
data class HeaderPair<out A : HeaderProvider, out B : HeaderProvider>(
    val first: A,
    val second: B,
) : HeaderProvider {
    override fun Headers.Builder.provide() {
        with(first) { provide() }
        with(second) { provide() }
    }
}

/**
 * Creates a pair of header implementations as [HeaderPair].
 */
infix fun <A : HeaderProvider, B : HeaderProvider> A.and(other: B) = HeaderPair(this, other)

/**
 * A header field that combines two other header fields. Used to decode [HeaderPair].
 */
class PairField<A : HeaderProvider, B : HeaderProvider>(
    val fieldA: HeaderField<A>,
    val fieldB: HeaderField<B>,
) : HeaderField<HeaderPair<A, B>> {
    override val field: String = "${fieldA.field}, ${fieldB.field}"

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): HeaderPair<A, B> = HeaderPair(fieldA.decode(headers), fieldB.decode(headers))

    override fun flatten(): List<HeaderField<*>> = fieldA.flatten() + fieldB.flatten()
}
