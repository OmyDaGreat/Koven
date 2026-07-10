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
inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, Res, ReqH, ResH>.register(
    crossinline handler: context(ReqH) (Req) -> Pair<Res, ResH>,
): RoutingHttpHandler =
    path bind method.toHttp4k to { req ->
        val headers = Headers.fromPairs(req.headers)

        val missing = requiredRequestHeaders.filter { headers[it] == null || headers[it]!!.all { value -> value.isBlank() } }
        if (missing.isNotEmpty()) { // TODO: Better error system
            return@to Response(BAD_REQUEST).body("Missing required headers: ${missing.joinToString { it.field }}")
        }

        val typedReqH = decodeRequestHeaders(headers)

        val body =
            if (Req::class == Unit::class) {
                Unit as Req
            } else {
                SpyderJson.default.decodeFromString<Req>(req.bodyString())
            }

        val (res, resH) = handler(typedReqH, body)
        val responseBody = if (Res::class == Unit::class) "" else SpyderJson.default.encodeToString(res)
        var http4kResponse = Response(Status.OK).body(responseBody)

        val builder = Headers.Builder().apply { add(resH) }
        builder.build().forEach { (k, v) ->
            v.forEach { http4kResponse = http4kResponse.header(k, it) }
        }

        http4kResponse
    }
