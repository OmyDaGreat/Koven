package xyz.malefic.koven.api

import arrow.core.raise.Raise
import xyz.malefic.koven.core.field.Cookie
import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.error.Issue

/**
 * A wrapper for the response body and its header(s).
 */
data class ApiResponse<Res, ResH>(
    val status: Int = 200,
    val body: Res,
    val headers: ResH,
    val cookies: List<Cookie> = emptyList(),
) {
    constructor(body: Res, headers: ResH, cookies: List<Cookie> = emptyList()) : this(200, body, headers, cookies)

    /**
     * Adds a status code to the response.
     */
    infix fun with(status: Int) = copy(status = status)

    /**
     * Adds a cookie to the response.
     */
    infix fun with(cookie: Cookie) = copy(cookies = cookies + cookie)

    /**
     * Adds multiple cookies to the response.
     */
    infix fun with(newCookies: List<Cookie>) = copy(cookies = cookies + newCookies)

    /**
     * Adds multiple cookies to the response.
     */
    fun with(vararg newCookies: Cookie) = copy(cookies = cookies + newCookies)

    /**
     * Gets a cookie from the response by its field.
     */
    context(_: Raise<Issue>)
    operator fun <T> get(field: CookieField<T>): T = field.decode(cookies.associate { it.name to it.value })

    companion object {
        /**
         * Creates an [ApiResponse] with the given [status].
         */
        infix fun <Res> Res.with(status: Int): ApiResponse<Res, Empty> = ApiResponse(status, this, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <Res, ResH> Res.with(headers: ResH): ApiResponse<Res, ResH> = ApiResponse(this, headers)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <Res> Res.with(cookie: Cookie): ApiResponse<Res, Empty> = ApiResponse(this, Empty, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun <Res> Res.with(newCookies: List<Cookie>): ApiResponse<Res, Empty> = ApiResponse(this, Empty, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun <Res> Res.with(vararg newCookies: Cookie): ApiResponse<Res, Empty> = ApiResponse(this, Empty, newCookies.toList())

        /**
         * Creates an [ApiResponse] with the given [status].
         */
        infix fun <ResH> ResH.withHeaderStatus(status: Int): ApiResponse<Unit, ResH> = ApiResponse(status, Unit, this)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <ResH> ResH.withHeaderCookie(cookie: Cookie): ApiResponse<Unit, ResH> = ApiResponse(Unit, this, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun <ResH> ResH.withHeaderCookies(newCookies: List<Cookie>): ApiResponse<Unit, ResH> = ApiResponse(Unit, this, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun <ResH> ResH.withHeaderCookies(vararg newCookies: Cookie): ApiResponse<Unit, ResH> = ApiResponse(Unit, this, newCookies.toList())

        /**
         * Creates an [ApiResponse] with the given [body].
         */
        infix fun <Res> Int.with(body: Res): ApiResponse<Res, Empty> = ApiResponse(this, body, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <ResH> Int.withHeaders(headers: ResH): ApiResponse<Unit, ResH> = ApiResponse(this, Unit, headers)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun Int.with(cookie: Cookie): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun Int.with(newCookies: List<Cookie>): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun Int.with(vararg newCookies: Cookie): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, newCookies.toList())

        /**
         * Creates a blank [ApiResponse].
         */
        val Blank = ApiResponse(Unit, Empty)
    }
}
