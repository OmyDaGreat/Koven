package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.ApiResponse
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.SpyderJson

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest) { set("Authorization", "...") }
 * ```
 */
suspend inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, Res, ReqH, ResH>.call(
    request: Req,
    headers: ReqH,
    crossinline headerBlock: Headers.Builder.() -> Unit = {},
): ApiResponse<Res, ResH> {
    val json =
        if (Req::class == Unit::class) {
            null
        } else {
            SpyderJson.default.encodeToString(request)
        }

    val response =
        window.api
            .call(
                method,
                path,
                if (json != null) bodyOf(json, "application/json") else null,
                Headers.build {
                    add(this@call.requestHeaders)
                    add(headers)
                    headerBlock()
                },
            )

    val text = response.text().await()

    val rawHeaders = mutableMapOf<String, List<String>>()
    response.headers.asDynamic().forEach({ value: String, key: String ->
        rawHeaders[key] = listOf(value)
    })

    val body = if (Res::class == Unit::class) Unit as Res else SpyderJson.default.decodeFromString<Res>(text)

    return ApiResponse(body, decodeResponseHeaders(Headers(rawHeaders)))
}
