package xyz.malefic.spyder.server

import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.SpyderJson

/**
 * Creates a route for the given [ApiContract].
 */
inline fun <reified Req, reified Res, H : HeaderProvider> ApiContract<Req, Res, H>.register(
    crossinline handler: context(Headers) (Req) -> Res,
): RoutingHttpHandler =
    path bind method.toHttp4k to { req ->
        val headers = Headers.fromPairs(req.headers)

        val missing = requiredFields.filter { headers[it] == null }
        if (missing.isNotEmpty()) { // TODO: Better error system
            return@to Response(BAD_REQUEST).body("Missing required headers: ${missing.joinToString { it.field }}")
        }

        val body =
            if (Req::class == Unit::class) {
                Unit as Req
            } else {
                SpyderJson.default.decodeFromString<Req>(req.bodyString())
            }

        val response = handler(headers, body)

        val responseBody =
            if (Res::class == Unit::class) {
                ""
            } else {
                SpyderJson.default.encodeToString<Res>(response)
            }

        Response(Status.OK).body(responseBody)
    }
