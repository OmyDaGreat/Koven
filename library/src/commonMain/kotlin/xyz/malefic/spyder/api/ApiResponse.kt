package xyz.malefic.spyder.api

import arrow.core.raise.Raise
import xyz.malefic.spyder.core.CookieField
import xyz.malefic.spyder.core.CookieProvider
import xyz.malefic.spyder.core.HeaderProvider
import xyz.malefic.spyder.core.NoHeader
import xyz.malefic.spyder.error.Issue

/**
 * A wrapper for the response body and its header(s).
 */
data class ApiResponse<Res, ResH : HeaderProvider>(
    val body: Res,
    val headers: ResH,
    val cookies: List<CookieProvider> = emptyList(),
) {
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
    fun with(vararg newCookies: CookieProvider) = with(newCookies.toList())

    /**
     * Gets a cookie from the response by its field.
     */
    context(_: Raise<Issue>)
    operator fun <T> get(field: CookieField<T>): T = field.decode(cookies.flatMap { it.provide() }.associate { it.name to it.value })

    companion object {
        /**
         * Creates an [ApiResponse] with the given [headers].
         */
        infix fun <Res, ResH : HeaderProvider> Res.with(headers: ResH) = ApiResponse(this, headers)

        /**
         * Creates an [ApiResponse] with the given [cookie].
         */
        infix fun <Res> Res.with(cookie: CookieProvider) = ApiResponse(this, NoHeader).with(cookie)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        infix fun <Res> Res.with(newCookies: List<CookieProvider>) = ApiResponse(this, NoHeader).with(newCookies)

        /**
         * Creates an [ApiResponse] with the given [newCookies].
         */
        fun <Res> Res.with(vararg newCookies: CookieProvider) = ApiResponse(this, NoHeader).with(newCookies.toList())

        /**
         * Creates a blank [ApiResponse].
         */
        val Blank = ApiResponse(Unit, NoHeader)
    }
}
