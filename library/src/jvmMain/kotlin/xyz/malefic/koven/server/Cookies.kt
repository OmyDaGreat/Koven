package xyz.malefic.koven.server

import org.http4k.core.Response
import org.http4k.core.cookie.cookie
import xyz.malefic.koven.core.Cookie
import xyz.malefic.koven.core.SameSite

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
