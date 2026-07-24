package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import xyz.malefic.koven.error.Issue

/**
 * Base interface for all field decoders.
 */
interface KovenField<out T> {
    /**
     * The field name(s) for this field.
     */
    val fields: List<String>

    /**
     * Flattens the field into its constituent fields.
     */
    fun flatten(): List<KovenField<*>>
}

/**
 * A generic pair for composing values.
 */
data class KovenPair<out A, out B>(
    val first: A,
    val second: B,
)

/**
 * Base class for composite fields, handling field aggregation and flattening.
 */
abstract class KovenPairField<out A, out B>(
    open val fieldA: KovenField<A>,
    open val fieldB: KovenField<B>,
) : KovenField<KovenPair<A, B>> {
    override val fields: List<String> get() = fieldA.fields + fieldB.fields

    override fun flatten(): List<KovenField<*>> = fieldA.flatten() + fieldB.flatten()
}

/**
 * Represents empty fields for Headers, Query Params, Path Params, and Cookies.
 */
object Empty :
    HeaderField<Empty>,
    PathField<Empty>,
    QueryField<Empty>,
    CookieField<Empty> {
    override val field: String = ""
    override val name: String = ""
    override val fields: List<String> = emptyList()

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): Empty = this

    override fun encodeHeaders(value: Empty): Map<String, List<String>> = emptyMap()

    override fun flatten(): List<Nothing> = emptyList()

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    context(_: Raise<Issue>)
    override fun decode(value: Map<String, String>) = this

    override fun encodePath(value: Empty): Map<String, String> = emptyMap()

    override fun encodeCookies(value: Empty): List<Cookie> = emptyList()

    context(_: Raise<Issue>)
    override fun decode(params: QueryParams) = this

    override fun encodeQuery(value: Empty): Map<String, List<String>> = emptyMap()
}

/**
 * Flattens a [KovenPair] into a list of its constituent elements.
 */
fun KovenPair<*, *>?.flattenPair(): List<Any?> =
    this?.let { listOf(first.flattenPairInternal(), second.flattenPairInternal()) } ?: listOf(this)

/**
 * Recursively flattens a [KovenPair] or a single value into a list of its constituent elements.
 */
private fun Any?.flattenPairInternal(): List<Any?> =
    when (this) {
        is KovenPair<*, *> -> first.flattenPairInternal() + second.flattenPairInternal()
        else -> listOf(this)
    }
