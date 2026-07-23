package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import kotlinx.serialization.Serializable
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

/**
 * A representation of an HTTP cookie.
 */
@Serializable
data class Cookie(
    val name: String,
    val value: String,
    val maxAge: Long? = null,
    val path: String? = null,
    val domain: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: SameSite = SameSite.Lax,
) : CookieProvider {
    override fun provide(): List<Cookie> = listOf(this)
}

/**
 * Interface for anything that can contribute cookies to a response.
 */
interface CookieProvider {
    fun provide(): List<Cookie>
}

/**
 * Interface for cookie fields, allowing type-safe retrieval and definition.
 */
interface CookieField<out T> : KovenField<T> {
    val name: String

    override val fields: List<String> get() = listOf(name)

    override fun flatten(): List<CookieField<*>> = listOf(this)

    /**
     * The maximum age of the cookie in seconds.
     */
    fun maxAge(): Long? = null

    /**
     * The path for which the cookie is valid.
     */
    fun path(): String? = "/"

    /**
     * The domain for which the cookie is valid.
     */
    fun domain(): String? = null

    /**
     * Whether the cookie should only be sent over secure connections.
     */
    fun secure(): Boolean = false

    /**
     * Whether this cookie is intended to be server-side only.
     */
    fun isHttpOnly(): Boolean = false

    /**
     * The SameSite attribute of the cookie.
     */
    fun sameSite(): SameSite = SameSite.Lax

    context(_: Raise<Issue>)
    infix fun decode(cookies: Map<String, String>): T

    /**
     * Creates a [Cookie] using the properties defined in this field.
     */
    infix fun create(value: String): Cookie = Cookie(name, value, maxAge(), path(), domain(), secure(), isHttpOnly(), sameSite())

    /**
     * Creates an empty [Cookie] with the properties defined in this field for the purpose of clearing an already-existent cookie.
     */
    fun clear(): Cookie = Cookie(name, "", 0, path(), domain(), secure(), isHttpOnly(), sameSite())

    companion object {
        operator fun invoke(
            name: String,
            isHttpOnly: Boolean = false,
            maxAge: Long? = null,
            path: String? = "/",
            domain: String? = null,
            secure: Boolean = false,
            sameSite: SameSite = SameSite.Lax,
        ): CookieField<String> =
            object : CookieField<String> {
                override val name: String = name

                override fun maxAge(): Long? = maxAge

                override fun path(): String? = path

                override fun domain(): String? = domain

                override fun secure(): Boolean = secure

                override fun isHttpOnly(): Boolean = isHttpOnly

                override fun sameSite(): SameSite = sameSite

                context(_: Raise<Issue>)
                override fun decode(cookies: Map<String, String>): String =
                    ensureNotNull(cookies[name]) { BadRequestIssue("Missing required cookie: $name") }
            }
    }
}

/**
 * The SameSite attribute of the cookie.
 */
enum class SameSite {
    Strict,
    Lax,
    None,
}

/**
 * Optimizes cookie implementations for [Empty] on the left.
 */
@JvmName("andEmptyCookieProviderLeft")
infix fun <B : CookieProvider> Empty.and(other: B): B = other

/**
 * Optimizes cookie implementations for [Empty] on the right.
 */
@JvmName("andEmptyCookieProviderRight")
infix fun <A : CookieProvider> A.and(other: Empty): A = this

/**
 * A cookie field that is optional.
 */
class OptionalCookieField<out T>(
    val inner: CookieField<T>,
) : CookieField<T?> {
    override val name: String get() = inner.name

    override fun flatten(): List<CookieField<*>> = inner.flatten()

    override fun maxAge(): Long? = inner.maxAge()

    override fun path(): String? = inner.path()

    override fun domain(): String? = inner.domain()

    override fun secure(): Boolean = inner.secure()

    override fun isHttpOnly(): Boolean = inner.isHttpOnly()

    override fun sameSite(): SameSite = inner.sameSite()

    context(_: Raise<Issue>)
    override fun decode(cookies: Map<String, String>): T? {
        if (!cookies.containsKey(name)) return null
        return inner.decode(cookies)
    }
}

/**
 * Marks a cookie field as optional.
 */
fun <T> CookieField<T>.optional(): CookieField<T?> = OptionalCookieField(this)

/**
 * A cookie field that combines two other cookie fields.
 */
class CookiePairField<out A, out B>(
    override val fieldA: CookieField<A>,
    override val fieldB: CookieField<B>,
) : KovenPairField<A, B>(fieldA, fieldB),
    CookieField<KovenPair<A, B>> {
    override val name: String = "${fieldA.name}, ${fieldB.name}"
    override val fields: List<String> get() = super<KovenPairField>.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<CookieField<*>> = super<KovenPairField>.flatten() as List<CookieField<*>>

    context(_: Raise<Issue>)
    override fun decode(cookies: Map<String, String>): KovenPair<A, B> = KovenPair(fieldA.decode(cookies), fieldB.decode(cookies))
}

/**
 * Creates a pair of cookie fields as [CookiePairField].
 */
infix fun <A, B> CookieField<A>.and(other: CookieField<B>): CookiePairField<A, B> = CookiePairField(this, other)

/**
 * Creates a pair of cookie fields as [CookiePairField], optimizing for [Empty] on the left.
 */
@JvmName("andEmptyCookieFieldLeft")
infix fun <B> Empty.and(other: CookieField<B>): CookieField<B> = other

/**
 * Creates a pair of cookie fields as [CookiePairField], optimizing for [Empty] on the right.
 */
@JvmName("andEmptyCookieFieldRight")
infix fun <A> CookieField<A>.and(other: Empty): CookieField<A> = this
