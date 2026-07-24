package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

/**
 * A wrapper for query parameters that supports multiple values.
 */
class QueryParams(
    private val data: Map<String, List<String>> = emptyMap(),
) : Map<String, List<String>> by data {
    /**
     * Gets all values for a given key.
     */
    override operator fun get(key: String): List<String>? = data[key]

    /**
     * Gets the first value for a given key.
     */
    fun getFirst(key: String): String? = data[key]?.firstOrNull()

    /**
     * Combines two [QueryParams] instances into a single one.
     */
    operator fun plus(other: QueryParams): QueryParams =
        build {
            add(this@QueryParams)
            add(other)
        }

    companion object {
        fun build(block: Builder.() -> Unit): QueryParams = Builder().apply(block).build()

        /**
         * Converts a collection of pairs into [QueryParams].
         */
        fun fromPairs(pairs: Iterable<Pair<String, String?>>): QueryParams =
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
            map.getOrPut(name) { mutableListOf() }.add(value)
        }

        operator fun set(
            name: String,
            value: String,
        ) {
            map[name] = mutableListOf(value)
        }

        @IgnorableReturnValue
        fun add(params: QueryParams) =
            apply {
                params.forEach { (k, v) ->
                    v.forEach { append(k, it) }
                }
            }

        fun build(): QueryParams = QueryParams(data = map.mapValues { it.value.toList() })
    }
}

/**
 * Interface for decoding query parameters on the server.
 */
interface QueryField<T> : KovenField<T> {
    /**
     * The field names for this query parameter.
     */
    override val fields: List<String>

    /**
     * Display name for this field, derived from [fields].
     */
    val name: String get() = fields.joinToString(", ")

    context(_: Raise<Issue>)
    fun decode(params: QueryParams): T

    fun encodeQuery(value: T): Map<String, List<String>>

    /**
     * Creates a [QueryParams] object from the given [value].
     */
    fun create(value: T): QueryParams = QueryParams(encodeQuery(value))

    /**
     * Flattens the [QueryField] into its constituent fields.
     */
    override fun flatten(): List<QueryField<*>> = listOf(this)

    companion object {
        operator fun invoke(name: String): QueryField<String> =
            object : QueryField<String> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): String =
                    ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }

                override fun encodeQuery(value: String): Map<String, List<String>> = mapOf(name to listOf(value))
            }

        fun int(name: String): QueryField<Int> =
            object : QueryField<Int> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): Int {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer for query parameter: $name"))
                }

                override fun encodeQuery(value: Int): Map<String, List<String>> = mapOf(name to listOf(value.toString()))
            }

        fun long(name: String): QueryField<Long> =
            object : QueryField<Long> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): Long {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toLongOrNull() ?: raise(BadRequestIssue("Invalid long for query parameter: $name"))
                }

                override fun encodeQuery(value: Long): Map<String, List<String>> = mapOf(name to listOf(value.toString()))
            }

        fun boolean(name: String): QueryField<Boolean> =
            object : QueryField<Boolean> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): Boolean {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toBooleanStrictOrNull() ?: raise(BadRequestIssue("Invalid boolean for query parameter: $name"))
                }

                override fun encodeQuery(value: Boolean): Map<String, List<String>> = mapOf(name to listOf(value.toString()))
            }

        fun list(name: String): QueryField<List<String>> =
            object : QueryField<List<String>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): List<String> =
                    ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }

                override fun encodeQuery(value: List<String>): Map<String, List<String>> = mapOf(name to value)
            }

        fun intList(name: String): QueryField<List<Int>> =
            object : QueryField<List<Int>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): List<Int> {
                    val values = ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }
                    return values.map { it.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer in query parameter list: $name")) }
                }

                override fun encodeQuery(value: List<Int>): Map<String, List<String>> = mapOf(name to value.map { it.toString() })
            }

        fun longList(name: String): QueryField<List<Long>> =
            object : QueryField<List<Long>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decode(params: QueryParams): List<Long> {
                    val values = ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }
                    return values.map { it.toLongOrNull() ?: raise(BadRequestIssue("Invalid long in query parameter list: $name")) }
                }

                override fun encodeQuery(value: List<Long>): Map<String, List<String>> = mapOf(name to value.map { it.toString() })
            }
    }
}

/**
 * A query field that is optional.
 */
class OptionalQueryField<T>(
    val inner: QueryField<T>,
) : QueryField<T?> {
    override val fields: List<String> get() = inner.fields

    override fun flatten(): List<QueryField<*>> = inner.flatten()

    context(_: Raise<Issue>)
    override fun decode(params: QueryParams): T? {
        if (fields.none { params.containsKey(it) }) return null
        return inner.decode(params)
    }

    override fun encodeQuery(value: T?): Map<String, List<String>> = value?.let { inner.encodeQuery(it) } ?: emptyMap()
}

/**
 * Marks a query field as optional.
 */
fun <T> QueryField<T>.optional(): QueryField<T?> = OptionalQueryField(this)

/**
 * A query field that combines two other query fields.
 */
class QueryPairField<A, B>(
    override val fieldA: QueryField<A>,
    override val fieldB: QueryField<B>,
) : KovenPairField<A, B>(fieldA, fieldB),
    QueryField<KovenPair<A, B>> {
    override val fields: List<String> get() = super.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<QueryField<*>> = super<KovenPairField>.flatten() as List<QueryField<*>>

    context(_: Raise<Issue>)
    override fun decode(params: QueryParams): KovenPair<A, B> = KovenPair(fieldA.decode(params), fieldB.decode(params))

    override fun encodeQuery(value: KovenPair<A, B>): Map<String, List<String>> =
        fieldA.encodeQuery(value.first) + fieldB.encodeQuery(value.second)
}

/**
 * Creates a pair of query fields as [QueryPairField].
 */
infix fun <A, B> QueryField<A>.and(other: QueryField<B>): QueryPairField<A, B> = QueryPairField(this, other)

/**
 * Creates a pair of query fields as [QueryPairField], optimizing for [Empty] on the left.
 */
@JvmName("andEmptyQueryFieldLeft")
@Suppress("UNCHECKED_CAST")
infix fun <B> Empty.and(other: QueryField<B>): QueryField<B> = other

/**
 * Creates a pair of query fields as [QueryPairField], optimizing for [Empty] on the right.
 */
@JvmName("andEmptyQueryFieldRight")
@Suppress("UNCHECKED_CAST")
infix fun <A> QueryField<A>.and(other: Empty): QueryField<A> = this
