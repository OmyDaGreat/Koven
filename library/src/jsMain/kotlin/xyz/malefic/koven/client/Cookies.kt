package xyz.malefic.koven.client

import arrow.core.raise.Raise
import kotlinx.browser.document
import xyz.malefic.koven.core.Cookie
import xyz.malefic.koven.core.CookieField
import xyz.malefic.koven.core.SameSite
import xyz.malefic.koven.error.Issue

/**
 * Access to the browser's cookies.
 */
object Cookies {
    /**
     * Gets all cookies as a map.
     */
    fun all(): Map<String, String> =
        document.cookie
            .split(";")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=")
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }

    /**
     * Gets a cookie value by its field.
     */
    context(_: Raise<Issue>)
    operator fun <T> get(field: CookieField<T>): T = field.decode(all())

    /**
     * Sets a cookie.
     */
    operator fun set(
        name: String,
        value: String,
    ) {
        document.cookie = "$name=$value; path=/"
    }

    /**
     * Sets a cookie using its [CookieField].
     */
    operator fun set(
        field: CookieField<String>,
        value: String,
    ) {
        if (field.isHttpOnly()) return

        set(field.create(value))
    }

    /**
     * Sets a cookie using a [Cookie] object.
     */
    fun set(cookie: Cookie) {
        if (cookie.httpOnly) return

        var cookieString = "${cookie.name}=${cookie.value}"
        cookie.maxAge?.let { cookieString += "; max-age=$it" }
        cookie.path?.let { cookieString += "; path=$it" }
        cookie.domain?.let { cookieString += "; domain=$it" }
        if (cookie.secure) cookieString += "; secure"
        cookieString +=
            when (cookie.sameSite) {
                SameSite.Strict -> "; samesite=strict"
                SameSite.Lax -> "; samesite=lax"
                SameSite.None -> "; samesite=none"
            }
        document.cookie = cookieString
    }

    /**
     * Deletes a cookie by its name.
     */
    fun delete(name: String) {
        document.cookie = "$name=; max-age=0; path=/"
    }

    /**
     * Deletes a cookie by its field.
     */
    fun delete(field: CookieField<*>) {
        delete(field.name)
    }
}
