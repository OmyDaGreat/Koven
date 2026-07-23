package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

/**
 * Base interface for all parameter providers.
 */
interface KovenProvider

/**
 * Base interface for all field decoders.
 */
interface KovenField { // TODO: Support optionality natively
    /**
     * The field name(s) for this field.
     */
    val fields: List<String>

    /**
     * Flattens the field into its constituent fields.
     */
    fun flatten(): List<KovenField>
}

/**
 * A generic pair for composing providers.
 */
data class KovenPair<out A, out B>(
    val first: A,
    val second: B,
) : KovenProvider,
    HeaderProvider,
    PathProvider,
    QueryProvider {
    override fun Headers.Builder.provide() {
        (first as? HeaderProvider)?.let { with(it) { provide() } }
        (second as? HeaderProvider)?.let { with(it) { provide() } }
    }

    override fun providePath(): Map<String, String> =
        (first as? PathProvider)?.providePath().orEmpty() + (second as? PathProvider)?.providePath().orEmpty()

    override fun provideQuery(): Map<String, List<String>> =
        (first as? QueryProvider)?.provideQuery().orEmpty() + (second as? QueryProvider)?.provideQuery().orEmpty()
}

/**
 * Base calass for composite fields that handles fields aggregation and flattening.
 */
abstract class KovenPairField(
    open val fieldA: KovenField,
    open val fieldB: KovenField,
) : KovenField {
    override val fields: List<String> get() = fieldA.fields + fieldB.fields

    override fun flatten(): List<KovenField> = fieldA.flatten() + fieldB.flatten()
}

/**
 * Represents empty fields for Headers, Query Params, Path Params, and Cookies.
 */
object Empty :
    KovenProvider,
    HeaderProvider,
    PathProvider,
    QueryProvider,
    CookieProvider,
    HeaderField<Empty>,
    PathField<Empty>,
    QueryField<Empty>,
    CookieField<Empty> {
    override fun Headers.Builder.provide() {}

    override val field: String = ""
    override val name: String = ""
    override val fields: List<String> = emptyList()

    context(_: Raise<Issue>)
    override fun decode(headers: Headers): Empty = this

    override fun flatten(): List<Empty> = emptyList()

    override fun providePath(): Map<String, String> = emptyMap()

    override fun provideQuery(): Map<String, List<String>> = emptyMap()

    override fun provide(): List<Cookie> = emptyList()

    context(_: Raise<Issue>)
    override fun decodePath(params: Map<String, String>) = this

    context(_: Raise<Issue>)
    override fun decodeQuery(params: QueryParams) = this

    context(_: Raise<Issue>)
    override fun decode(cookies: Map<String, String>): Empty = this
}

/**
 * Creates a pair of providers.
 */
@JvmName("andProvider")
infix fun <A : KovenProvider, B : KovenProvider> A.and(other: B): KovenPair<A, B> = KovenPair(this, other)
