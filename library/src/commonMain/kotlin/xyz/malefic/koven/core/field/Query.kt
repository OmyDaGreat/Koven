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
) : Map<String, List<String>> by data,
    QueryProvider {
    /**
     * Gets all values for a given key.
     */
    override operator fun get(key: String): List<String>? = data[key]

    /**
     * Gets the first value for a given key.
     */
    fun getFirst(key: String): String? = data[key]?.firstOrNull()

    override fun provideQuery(): Map<String, List<String>> = data

    /**
     * Combines two [QueryParams] instances into a single one.
     */
    operator fun plus(other: QueryProvider): QueryParams =
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
        fun add(provider: QueryProvider) =
            apply {
                provider.provideQuery().forEach { (k, v) ->
                    v.forEach { append(k, it) }
                }
            }

        fun build(): QueryParams = QueryParams(data = map.mapValues { it.value.toList() })
    }
}

/**
 * Interface for providing query parameters on the client.
 */
interface QueryProvider : KovenProvider {
    fun provideQuery(): Map<String, List<String>>
}

/**
 * Interface for decoding query parameters on the server.
 */
interface QueryField<out T> : KovenField {
    /**
     * The field names for this query parameter.
     */
    override val fields: List<String>

    context(_: Raise<Issue>)
    fun decodeQuery(params: QueryParams): T

    /**
     * Flattens the [QueryField] into its constituent fields.
     */
    override fun flatten(): List<QueryField<*>> = listOf(this)

    companion object {
        operator fun invoke(name: String): QueryField<String> =
            object : QueryField<String> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): String =
                    ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
            }

        fun int(name: String): QueryField<Int> =
            object : QueryField<Int> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): Int {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer for query parameter: $name"))
                }
            }

        fun long(name: String): QueryField<Long> =
            object : QueryField<Long> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): Long {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toLongOrNull() ?: raise(BadRequestIssue("Invalid long for query parameter: $name"))
                }
            }

        fun boolean(name: String): QueryField<Boolean> =
            object : QueryField<Boolean> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): Boolean {
                    val value = ensureNotNull(params.getFirst(name)) { BadRequestIssue("Missing required query parameter: $name") }
                    return value.toBooleanStrictOrNull() ?: raise(BadRequestIssue("Invalid boolean for query parameter: $name"))
                }
            }

        fun list(name: String): QueryField<List<String>> =
            object : QueryField<List<String>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): List<String> =
                    ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }
            }

        fun intList(name: String): QueryField<List<Int>> =
            object : QueryField<List<Int>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): List<Int> {
                    val values = ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }
                    return values.map { it.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer in query parameter list: $name")) }
                }
            }

        fun longList(name: String): QueryField<List<Long>> =
            object : QueryField<List<Long>> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodeQuery(params: QueryParams): List<Long> {
                    val values = ensureNotNull(params[name]) { BadRequestIssue("Missing required query parameter: $name") }
                    return values.map { it.toLongOrNull() ?: raise(BadRequestIssue("Invalid long in query parameter list: $name")) }
                }
            }
    }
}

/**
 * Interface for a single query parameter.
 */
interface QueryParam : QueryProvider {
    val field: String
    val values: List<String>

    override fun provideQuery(): Map<String, List<String>> = mapOf(field to values)
}

/**
 * Optimizes query implementations for [Empty] on the left.
 */
@JvmName("andEmptyQueryProviderLeft")
infix fun <B : QueryProvider> Empty.and(other: B): B = other

/**
 * Optimizes query implementations for [Empty] on the right.
 */
@JvmName("andEmptyQueryProviderRight")
infix fun <A : QueryProvider> A.and(other: Empty): A = this

/**
 * A query field that combines two other query fields.
 */
class QueryPairField<out A, out B>(
    override val fieldA: QueryField<A>,
    override val fieldB: QueryField<B>,
) : KovenPairField(fieldA, fieldB),
    QueryField<KovenPair<A, B>> {
    override val fields: List<String> get() = super.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<QueryField<*>> = super<KovenPairField>.flatten() as List<QueryField<*>>

    context(_: Raise<Issue>)
    override fun decodeQuery(params: QueryParams): KovenPair<A, B> = KovenPair(fieldA.decodeQuery(params), fieldB.decodeQuery(params))
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
