package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

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
interface HeaderProvider : KovenProvider {
    fun Headers.Builder.provide()
}

/**
 * Interface for header fields.
 */
interface HeaderField<out T> : KovenField {
    val field: String

    /**
     * The fields for this header field.
     */
    override val fields: List<String>

    context(_: Raise<Issue>)
    fun decode(headers: Headers): T

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
            }

        fun list(name: String): HeaderField<List<String>> =
            object : HeaderField<List<String>> {
                override val field: String = name
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(headers: Headers): List<String> =
                    ensureNotNull(headers[name]) { BadRequestIssue("Missing required header: $name") }
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

    override fun flatten(): List<Nothing> = emptyList()
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
        override val fields: List<String> = listOf(field)

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
 * Optimizes header implementations for [Empty] on the left.
 */
@JvmName("andEmptyHeaderProviderLeft")
infix fun <B : HeaderProvider> Empty.and(other: B): B = other

/**
 * Optimizes header implementations for [Empty] on the right.
 */
@JvmName("andEmptyHeaderProviderRight")
infix fun <A : HeaderProvider> A.and(other: Empty): A = this

/**
 * A header field that combines two other header fields. Used to decode [KovenPair].
 */
class HeaderPairField<A, B>(
    override val fieldA: HeaderField<A>,
    override val fieldB: HeaderField<B>,
) : KovenPairField(fieldA, fieldB),
    HeaderField<KovenPair<A, B>> {
    override val field: String = "${fieldA.field}, ${fieldB.field}"
    override val fields: List<String> get() = super.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<HeaderField<*>> = super<KovenPairField>.flatten() as List<HeaderField<*>>

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): KovenPair<A, B> = KovenPair(fieldA.decode(headers), fieldB.decode(headers))
}

/**
 * Creates a pair of header fields as [HeaderPairField].
 */
infix fun <A, B> HeaderField<A>.and(other: HeaderField<B>): HeaderPairField<A, B> =
    HeaderPairField(this, other)

/**
 * Creates a pair of header fields as [HeaderPairField], optimizing for [Empty] on the left.
 */
@JvmName("andEmptyHeaderFieldLeft")
infix fun <B> Empty.and(other: HeaderField<B>): HeaderField<B> = other

/**
 * Creates a pair of header fields as [HeaderPairField], optimizing for [Empty] on the right.
 */
@JvmName("andEmptyHeaderFieldRight")
infix fun <A> HeaderField<A>.and(other: Empty): HeaderField<A> = this
