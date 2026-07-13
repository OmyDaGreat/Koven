package xyz.malefic.spyder

import arrow.core.raise.Raise
import kotlinx.serialization.Serializable

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
 * @param requiredRequestHeaders The set of required request header fields for the API endpoint.
 * @param requiredResponseHeaders The set of required response header fields for the API endpoint.
 * @param requestHeaderDecoder The [HeaderField] to decode the request headers.
 * @param responseHeaderDecoder The [HeaderField] to decode the response headers.
 * @param pathDecoder The [PathField] to decode the path parameters.
 * @param queryDecoder The [QueryField] to decode the query parameters.
 *
 * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
 * @param ReqH The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
 * @param ResH The [HeaderProvider] type of the response headers. Use [NoHeaders] for no headers.
 * @param PathP The [PathProvider] type of the path parameters. Use [NoParams] for no path parameters.
 * @param QueryP The [QueryProvider] type of the query parameters. Use [NoParams] for no query parameters.
 */
abstract class ApiContract<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider>(
    val path: String,
    val method: Method = Method.POST,
    val requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
    val requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
    @Suppress("UNCHECKED_CAST")
    val requestHeaderDecoder: HeaderField<ReqH> = NoHeaders as HeaderField<ReqH>,
    @Suppress("UNCHECKED_CAST")
    val responseHeaderDecoder: HeaderField<ResH> = NoHeaders as HeaderField<ResH>,
    @Suppress("UNCHECKED_CAST")
    val pathDecoder: PathField<PathP> = NoParams as PathField<PathP>,
    @Suppress("UNCHECKED_CAST")
    val queryDecoder: QueryField<QueryP> = NoParams as QueryField<QueryP>,
) {
    /**
     * The set of query parameters allowed for the API endpoint.
     */
    open val queryParams: List<String> = emptyList()

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
    open fun decodeQuery(params: Map<String, List<String>>): QueryP = queryDecoder.decodeQuery(params)

    /**
     * A contract for an API endpoint that uses a custom header, only for the response.
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
     *
     * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     * @param ResH The [HeaderProvider] type of the response headers. Use [NoHeaders] for no headers.
     */
    abstract class Response<Req, Res, ResH : HeaderProvider>( // TODO: Make a builder of some sort to replace abstract class wrappers
        path: String,
        method: Method = Method.POST,
        requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
        @Suppress("UNCHECKED_CAST")
        responseHeaderDecoder: HeaderField<ResH> = NoHeaders as HeaderField<ResH>,
    ) : ApiContract<Req, Res, NoHeaders, ResH, NoParams, NoParams>(
            path,
            method,
            requiredResponseHeaders = requiredResponseHeaders,
            responseHeaderDecoder = responseHeaderDecoder,
        )

    /**
     * A contract for an API endpoint that uses a custom header, only for the request.
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
     *
     * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     * @param ReqH The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
     */
    abstract class Request<Req, Res, ReqH : HeaderProvider>(
        path: String,
        method: Method = Method.POST,
        requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
        @Suppress("UNCHECKED_CAST")
        requestHeaderDecoder: HeaderField<ReqH> = NoHeaders as HeaderField<ReqH>,
    ) : ApiContract<Req, Res, ReqH, NoHeaders, NoParams, NoParams>(
            path,
            method,
            requiredRequestHeaders = requiredRequestHeaders,
            requestHeaderDecoder = requestHeaderDecoder,
        )

    /**
     * A contract for an API endpoint that uses no custom headers.
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
     *
     * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     */
    abstract class Plain<Req, Res>(
        path: String,
        method: Method = Method.POST,
    ) : ApiContract<Req, Res, NoHeaders, NoHeaders, NoParams, NoParams>(path, method)

    /**
     * A contract for a simple GET-style API endpoint (no request body, no custom headers).
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.GET].
     *
     * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     */
    abstract class Basic<Res>(
        path: String,
        method: Method = Method.GET,
    ) : ApiContract<Unit, Res, NoHeaders, NoHeaders, NoParams, NoParams>(path, method)

    /**
     * A contract for an API endpoint that uses path parameters.
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
     * @param pathDecoder The [PathField] to decode the path parameters.
     * @param queryDecoder The [QueryField] to decode the query parameters.
     *
     * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     * @param PathP The [PathProvider] type of the path parameters.
     * @param QueryP The [QueryProvider] type of the query parameters.
     */
    abstract class Parameters<Req, Res, PathP : PathProvider, QueryP : QueryProvider>(
        path: String,
        method: Method = Method.POST,
        pathDecoder: PathField<PathP>,
        queryDecoder: QueryField<QueryP>,
    ) : ApiContract<Req, Res, NoHeaders, NoHeaders, PathP, QueryP>(
            path,
            method,
            pathDecoder = pathDecoder,
            queryDecoder = queryDecoder,
        )
}

/**
 * A wrapper for the response body and its type-safe headers.
 */
data class ApiResponse<Res, ResH>(
    val body: Res,
    val headers: ResH,
)

/**
 * A contract for the common "health" endpoint.
 */
class HealthContract : ApiContract.Basic<String>("health", Method.GET)

/**
 * A contract for the common "ping" endpoint.
 */
class PingContract : ApiContract.Basic<String>("ping", Method.GET)

/**
 * A contract for an example "auth/ping" endpoint.
 */
class SecurePingContract : ApiContract.Request<Unit, String, BearerAuth>("auth/ping", Method.GET, listOf(BearerAuth), BearerAuth)
