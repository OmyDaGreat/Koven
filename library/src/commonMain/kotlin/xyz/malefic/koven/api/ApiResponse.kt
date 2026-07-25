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
    fun withStatus(status: Int) = copy(status = status)

    /**
     * Adds headers to the response.
     */
    fun <NewResH> withHeaders(headers: NewResH) = ApiResponse(status, body, headers, cookies)

    /**
     * Adds a cookie to the response.
     */
    fun withCookie(cookie: Cookie) = copy(cookies = cookies + cookie)

    /**
     * Adds multiple cookies to the response.
     */
    fun withCookies(newCookies: List<Cookie>) = copy(cookies = cookies + newCookies)

    /**
     * Adds multiple cookies to the response.
     */
    fun withCookies(vararg newCookies: Cookie) = copy(cookies = cookies + newCookies)

    /**
     * Gets a cookie from the response by its field.
     */
    context(_: Raise<Issue>)
    operator fun <T> get(field: CookieField<T>): T = field.decode(cookies.associate { it.name to it.value })

    companion object {
        /**
         * Creates an [ApiResponse] with the given [status].
         */
        infix fun <Res> Res.withStatus(status: Int): ApiResponse<Res, Empty> = ApiResponse(status, this, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <Res, ResH> Res.withHeaders(headers: ResH): ApiResponse<Res, ResH> = ApiResponse(this, headers)

        /**
         * Creates an [ApiResponse] with the given [cookies].
         */
        fun <Res> Res.withCookies(vararg cookies: Cookie): ApiResponse<Res, Empty> = ApiResponse(this, Empty, cookies.toList())

        /**
         * Creates an [ApiResponse] with the given [cookies].
         */
        infix fun <Res> Res.withCookies(cookies: List<Cookie>): ApiResponse<Res, Empty> = ApiResponse(this, Empty, cookies)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <Res> Res.withCookie(cookie: Cookie): ApiResponse<Res, Empty> = ApiResponse(this, Empty, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [body].
         */
        infix fun <Res> Int.withBody(body: Res): ApiResponse<Res, Empty> = ApiResponse(this, body, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <ResH> Int.withHeaders(headers: ResH): ApiResponse<Unit, ResH> = ApiResponse(this, Unit, headers)

        /**
         * Creates an [ApiResponse] with the given [cookies].
         */
        fun Int.withCookies(vararg cookies: Cookie): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, cookies.toList())

        /**
         * Creates an [ApiResponse] with the given [cookies].
         */
        infix fun Int.withCookies(cookies: List<Cookie>): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, cookies)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun Int.withCookie(cookie: Cookie): ApiResponse<Unit, Empty> = ApiResponse(this, Unit, Empty, listOf(cookie))

        @Suppress("UNCHECKED_CAST")
        class Builder<Res, ResH>(
            var status: Int = 200,
            var body: Res = Unit as Res,
            var headers: ResH = Empty as ResH,
            var cookies: List<Cookie> = emptyList(),
        ) {
            fun build() = ApiResponse(status, body, headers, cookies)
        }
    }
}
