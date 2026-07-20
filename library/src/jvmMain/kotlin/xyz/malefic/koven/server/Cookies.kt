package xyz.malefic.koven.server

import arrow.core.raise.Raise
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import xyz.malefic.koven.core.Cookie
import xyz.malefic.koven.core.CookieField
import xyz.malefic.koven.core.SameSite
import xyz.malefic.koven.error.Issue

/**
 * Attaches a Koven [xyz.malefic.koven.core.Cookie] to the [org.http4k.core.Response].
 */
fun Response.cookie(cookie: Cookie): Response =
    cookie(
        org.http4k.core.cookie.Cookie(
            name = cookie.name,
            value = cookie.value,
            maxAge = cookie.maxAge,
            path = cookie.path,
            domain = cookie.domain,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            sameSite =
                when (cookie.sameSite) {
                    SameSite.Strict -> org.http4k.core.cookie.SameSite.Strict
                    SameSite.Lax -> org.http4k.core.cookie.SameSite.Lax
                    SameSite.None -> org.http4k.core.cookie.SameSite.None
                },
        ),
    )

/**
 * Gets a cookie from the [org.http4k.core.Request] by its [field].
 */
context(_: Raise<Issue>)
operator fun <T> Request.get(field: CookieField<T>): T = field.decode(cookies().associate { it.name to it.value })
