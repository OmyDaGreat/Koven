package xyz.malefic.koven.api

import arrow.core.raise.Raise
import kotlinx.serialization.serializer
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.HttpMethod.GET
import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.CookieProvider
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.core.field.HeaderField
import xyz.malefic.koven.core.field.HeaderProvider
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.core.field.PathField
import xyz.malefic.koven.core.field.PathProvider
import xyz.malefic.koven.core.field.QueryField
import xyz.malefic.koven.core.field.QueryParams
import xyz.malefic.koven.core.field.QueryProvider
import xyz.malefic.koven.error.Issue

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param httpMethod The HTTP method to use for the API endpoint. Defaults to [HttpMethod.POST].
 * @param requestHeaderDecoder The [HeaderField] to decode the request headers.
 * @param responseHeaderDecoder The [HeaderField] to decode the response headers.
 * @param pathDecoder The [PathField] to decode the path parameters.
 * @param queryDecoder The [QueryField] to decode the query parameters.
 * @param cookieDecoder The [CookieField] to decode the request cookies.
 * @param requestFormat The [BodyFormat] to use for the request body. Defaults to [UnitFormat] or [JsonFormat].
 * @param responseFormat The [BodyFormat] to use for the response body. Defaults to [UnitFormat] or [JsonFormat].
 * @param isProtected Whether the API endpoint requires authentication. Defaults to `false`.
 *
 * @param Req The type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The type of the response body. If the response body is empty, this should be `Unit`.
 * @param ReqH The [HeaderProvider] type of the request headers. Use [Empty] for no headers.
 * @param ResH The [HeaderProvider] type of the response headers. Use [Empty] for no headers.
 * @param PathP The [PathProvider] type of the path parameters. Use [Empty] for no path parameters.
 * @param QueryP The [QueryProvider] type of the query parameters. Use [Empty] for no query parameters.
 * @param CookieP The [CookieProvider] type of the request cookies. Use [Empty] for no cookies.
 */
@Suppress("ktlint:standard:max-line-length")
abstract class ApiContract<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider, CookieP : CookieProvider>(
    val path: String,
    val httpMethod: HttpMethod = HttpMethod.POST,
    @Suppress("UNCHECKED_CAST")
    val requestHeaderDecoder: HeaderField<ReqH> = Empty as HeaderField<ReqH>,
    @Suppress("UNCHECKED_CAST")
    val responseHeaderDecoder: HeaderField<ResH> = Empty as HeaderField<ResH>,
    @Suppress("UNCHECKED_CAST")
    val pathDecoder: PathField<PathP> = Empty as PathField<PathP>,
    @Suppress("UNCHECKED_CAST")
    val queryDecoder: QueryField<QueryP> = Empty as QueryField<QueryP>,
    @Suppress("UNCHECKED_CAST")
    val cookieDecoder: CookieField<CookieP> = Empty as CookieField<CookieP>,
    val requestFormat: BodyFormat<Req>,
    val responseFormat: BodyFormat<Res>,
    val isProtected: Boolean = false,
) {
    /**
     * Decodes the request headers into the type [ReqH]. Can be overridden to provide custom header decoding.
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeRequestHeaders(headers: Headers): ReqH = requestHeaderDecoder.decode(headers)

    /**
     * Decodes the response headers into the type [ResH]. Can be overridden to provide custom header decoding.
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeResponseHeaders(headers: Headers): ResH = responseHeaderDecoder.decode(headers)

    /**
     * Decodes the path parameters into the type [PathP].
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodePath(params: Map<String, String>): PathP = pathDecoder.decodePath(params)

    /**
     * Decodes the query parameters into the type [QueryP].
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeQuery(params: QueryParams): QueryP = queryDecoder.decodeQuery(params)

    /**
     * Decodes the request cookies into the type [CookieP].
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeCookies(cookies: Map<String, String>): CookieP = cookieDecoder.decode(cookies)
}

/**
 * Entry point for creating an [ApiContract] via builder.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified Req, reified Res> apiContract(path: String): ApiContractBuilder<Req, Res, Empty, Empty, Empty, Empty, Empty> {
    val reqFormat =
        if (Req::class == Unit::class) {
            UnitFormat as BodyFormat<Req>
        } else {
            KovenConfig.serialization.createFormat(serializer<Req>())
        }
    val resFormat =
        if (Res::class == Unit::class) {
            UnitFormat as BodyFormat<Res>
        } else {
            KovenConfig.serialization.createFormat(serializer<Res>())
        }

    return ApiContractBuilder(
        path = path,
        requestHeaderDecoder = Empty as HeaderField<Empty>,
        responseHeaderDecoder = Empty as HeaderField<Empty>,
        pathDecoder = Empty as PathField<Empty>,
        queryDecoder = Empty as QueryField<Empty>,
        cookieDecoder = Empty as CookieField<Empty>,
        requestFormat = reqFormat,
        responseFormat = resFormat,
    )
}

/**
 * A contract for the common "health" endpoint.
 */
val HealthContract = apiContract<Unit, String>("health").method(GET).build()

/**
 * A contract for the common "ping" endpoint.
 */
val PingContract = apiContract<Unit, String>("ping").method(GET).build()

/**
 * A contract for an example "auth/ping" endpoint.
 */
val SecurePingContract = apiContract<Unit, String>("auth/ping").method(GET).protected().build()
