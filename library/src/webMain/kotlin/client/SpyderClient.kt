package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.NoHeaders
import xyz.malefic.spyder.SpyderJson

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest) { set("Authorization", "...") }
 * ```
 */
suspend inline fun <reified Req, reified Res, H : HeaderProvider> ApiContract<Req, Res, H>.call(
    request: Req,
    headers: H,
    crossinline headerBlock: Headers.Builder.() -> Unit = {},
): Res {
    val json =
        if (Req::class == Unit::class) {
            null
        } else {
            SpyderJson.default.encodeToString(request)
        }

    val text =
        window.api
            .call(
                method,
                path,
                if (json != null) bodyOf(json, "application/json") else null,
                Headers.build {
                    add(this@call.headers)
                    add(headers)
                    headerBlock()
                },
            ).text()
            .await()

    return if (Res::class == Unit::class) {
        Unit as Res
    } else {
        SpyderJson.default.decodeFromString<Res>(text)
    }
}

/**
 * Extension to make calling contracts more ergonomic. Specifically for [NoHeaders] contracts.
 */
suspend inline fun <reified Req, reified Res> ApiContract<Req, Res, NoHeaders>.call(
    request: Req,
    crossinline headerBlock: Headers.Builder.() -> Unit = {},
): Res = call(request, NoHeaders, headerBlock)
