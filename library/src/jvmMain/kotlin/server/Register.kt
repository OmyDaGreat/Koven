package xyz.malefic.spyder.server

import kotlinx.serialization.serializer
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.SpyderJson

inline fun <reified Req, reified Res> ApiContract<Req, Res>.register(crossinline handler: (Req) -> Res): RoutingHttpHandler =
    path bind method.toHttp4k to { req ->
        val body =
            if (Req::class == Unit::class) {
                Unit as Req
            } else {
                SpyderJson.default.decodeFromString(SpyderJson.default.serializersModule.serializer<Req>(), req.bodyString())
            }

        val response = handler(body)

        val responseBody =
            if (Res::class == Unit::class) {
                ""
            } else {
                SpyderJson.default.encodeToString(SpyderJson.default.serializersModule.serializer<Res>(), response)
            }

        Response(Status.OK).body(responseBody)
    }
