package xyz.malefic.koven.api

import arrow.core.raise.Raise
import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.CookieProvider
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.core.field.HeaderProvider
import xyz.malefic.koven.error.Issue

/**
 * A wrapper for the response body and its header(s).
 */
data class ApiResponse<Res, ResH : HeaderProvider>(
    val status: Int = 200,
    val body: Res,
    val headers: ResH,
    val cookies: List<CookieProvider> = emptyList(),
) {
    constructor(body: Res, headers: ResH, cookies: List<CookieProvider> = emptyList()) : this(200, body, headers, cookies)

    /**
     * Adds a status code to the response.
     */
    infix fun with(status: Int) = copy(status = status)

    /**
     * Adds a cookie to the response.
     */
    infix fun with(cookie: CookieProvider) = copy(cookies = cookies + cookie)

    /**
     * Adds multiple cookies to the response.
     */
    infix fun with(newCookies: List<CookieProvider>) = copy(cookies = cookies + newCookies)

    /**
     * Adds multiple cookies to the response.
     */
    fun with(vararg newCookies: CookieProvider) = copy(cookies = cookies + newCookies)

    /**
     * Gets a cookie from the response by its field.
     */
    context(_: Raise<Issue>)
    operator fun <T> get(field: CookieField<T>): T = field.decode(cookies.flatMap { it.provide() }.associate { it.name to it.value })

    companion object {
        /**
         * Creates an [ApiResponse] with the given [status].
         */
        infix fun <Res> Res.with(status: Int) = ApiResponse(status, this, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <Res, ResH : HeaderProvider> Res.with(headers: ResH) = ApiResponse(this, headers)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <Res> Res.with(cookie: CookieProvider) = ApiResponse(this, Empty, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun <Res> Res.with(newCookies: List<CookieProvider>) = ApiResponse(this, Empty, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun <Res> Res.with(vararg newCookies: CookieProvider) = ApiResponse(this, Empty, newCookies.toList())

        /**
         * Creates an [ApiResponse] with the given [status].
         */
        infix fun <ResH : HeaderProvider> ResH.with(status: Int) = ApiResponse(status, Unit, this)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <ResH : HeaderProvider> ResH.with(cookie: CookieProvider) = ApiResponse(Unit, this, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun <ResH : HeaderProvider> ResH.with(newCookies: List<CookieProvider>) = ApiResponse(Unit, this, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun <ResH : HeaderProvider> ResH.with(vararg newCookies: CookieProvider) = ApiResponse(Unit, this, newCookies.toList())

        /**
         * Creates an [ApiResponse] with the given [body].
         */
        infix fun <Res> Int.with(body: Res) = ApiResponse(this, body, Empty)

        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <ResH : HeaderProvider> Int.with(headers: ResH) = ApiResponse(this, Unit, headers)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun Int.with(cookie: CookieProvider) = ApiResponse(this, Unit, Empty, listOf(cookie))

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun Int.with(newCookies: List<CookieProvider>) = ApiResponse(this, Unit, Empty, newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun Int.with(vararg newCookies: CookieProvider) = ApiResponse(this, Unit, Empty, newCookies.toList())

        /**
         * Creates a blank [ApiResponse].
         */
        val Blank = ApiResponse(Unit, Empty)
    }
}
