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
     * Whether this cookie is intended to be server-side only.
     */
    val isHttpOnly: Boolean get() = false

    /**
     * The maximum age of the cookie in seconds.
     */
    val maxAge: Long? get() = null

    /**
     * The path for which the cookie is valid.
     */
    val path: String? get() = "/"

    /**
     * The domain for which the cookie is valid.
     */
    val domain: String? get() = null

    /**
     * Whether the cookie should only be sent over secure connections.
     */
    val secure: Boolean get() = false

    /**
     * The SameSite attribute of the cookie.
     */
    val sameSite: SameSite get() = SameSite.Lax

    context(_: Raise<Issue>)
    fun decode(cookies: Map<String, String>): T

    /**
     * Creates a [Cookie] using the properties defined in this field.
     */
    fun create(value: String): Cookie =
        Cookie(
            name = name,
            value = value,
            maxAge = maxAge,
            path = path,
            domain = domain,
            secure = secure,
            httpOnly = isHttpOnly,
            sameSite = sameSite,
        )

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
                override val isHttpOnly: Boolean = isHttpOnly
                override val maxAge: Long? = maxAge
                override val path: String? = path
                override val domain: String? = domain
                override val secure: Boolean = secure
                override val sameSite: SameSite = sameSite

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
