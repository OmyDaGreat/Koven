package xyz.malefic.spyder

import arrow.core.raise.Raise

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
 * @param requiredRequestHeaders The set of required request header fields for the API endpoint.
 * @param requiredResponseHeaders The set of required response header fields for the API endpoint.
 *
 * @param Req The [kotlinx.serialization.Serializable] type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The [kotlinx.serialization.Serializable] type of the request body. If the response body is empty, this should be `Unit`.
 * @param ReqH The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
 * @param ResH The [HeaderProvider] type of the response headers. Use [NoHeaders] for no headers.
 */
abstract class ApiContract<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider>(
    val path: String,
    val method: Method = Method.POST,
    val requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
    val requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
    val requestHeaderDecoder: HeaderField<ReqH>? = null,
    val responseHeaderDecoder: HeaderField<ResH>? = null,
) {
    open val requestHeaders: Headers = Headers()
    open val responseHeaders: Headers = Headers()

    /**
     * Decodes the request headers into the type [ReqH]. Can be overridden to provide custom header decoding.
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeRequestHeaders(headers: Headers): ReqH = requestHeaderDecoder?.decode(headers) ?: (NoHeaders as ReqH)

    /**
     * Decodes the response headers into the type [ResH]. Can be overridden to provide custom header decoding.
     */
    @Suppress("UNCHECKED_CAST")
    context(_: Raise<Issue>)
    open fun decodeResponseHeaders(headers: Headers): ResH = responseHeaderDecoder?.decode(headers) ?: (NoHeaders as ResH)

    /**
     * A contract for an API endpoint that uses a custom header, only for the response.
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.POST].
     *
     * @param Req The [kotlinx.serialization.Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [kotlinx.serialization.Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     * @param ResH The [HeaderProvider] type of the response headers. Use [NoHeaders] for no headers.
     */
    abstract class Response<Req, Res, ResH : HeaderProvider>(
        path: String,
        method: Method = Method.POST,
        requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
        responseHeaderDecoder: HeaderField<ResH>? = null,
    ) : ApiContract<Req, Res, NoHeaders, ResH>(
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
     * @param Req The [kotlinx.serialization.Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [kotlinx.serialization.Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     * @param ReqH The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
     */
    abstract class Request<Req, Res, ReqH : HeaderProvider>(
        path: String,
        method: Method = Method.POST,
        requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
        requestHeaderDecoder: HeaderField<ReqH>? = null,
    ) : ApiContract<Req, Res, ReqH, NoHeaders>(
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
     * @param Req The [kotlinx.serialization.Serializable] type of the request body. If the request body is empty, this should be `Unit`.
     * @param Res The [kotlinx.serialization.Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     */
    abstract class Plain<Req, Res>(
        path: String,
        method: Method = Method.POST,
    ) : ApiContract<Req, Res, NoHeaders, NoHeaders>(path, method)

    /**
     * A contract for a simple GET-style API endpoint (no request body, no custom headers).
     *
     * @param path The path of the API endpoint. Automatically prefixed with "api/".
     * @param method The HTTP method to use for the API endpoint. Default is [Method.GET].
     *
     * @param Res The [kotlinx.serialization.Serializable] type of the request body. If the response body is empty, this should be `Unit`.
     */
    abstract class Basic<Res>(
        path: String,
        method: Method = Method.GET,
    ) : ApiContract<Unit, Res, NoHeaders, NoHeaders>(path, method)
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
