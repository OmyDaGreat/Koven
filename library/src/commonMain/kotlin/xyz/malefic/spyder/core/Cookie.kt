package xyz.malefic.spyder.core

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import kotlinx.serialization.Serializable
import xyz.malefic.spyder.error.BadRequestIssue
import xyz.malefic.spyder.error.Issue

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
interface CookieField<out T> {
    val name: String

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
 * Enum representing the SameSite attribute of a cookie.
 */
enum class SameSite {
    Strict,
    Lax,
    None,
}
