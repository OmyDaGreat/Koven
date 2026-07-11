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
 *
 * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
 * @param ReqH The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
 * @param ResH The [HeaderProvider] type of the response headers. Use [NoHeaders] for no headers.
 */
abstract class ApiContract<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider>(
    val path: String,
    val method: Method = Method.POST,
    val requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
    val requiredResponseHeaders: List<HeaderField<*>> = emptyList(), // TODO: Add a standard way to validate responses(?)
) {
    open val requestHeaders: Headers = Headers()
    open val responseHeaders: Headers = Headers()

    /**
     * Decodes the request headers into the type [ReqH].
     */
    context(_: Raise<Issue>)
    abstract fun decodeRequestHeaders(headers: Headers): ReqH

    /**
     * Decodes the response headers into the type [ResH].
     */
    context(_: Raise<Issue>)
    abstract fun decodeResponseHeaders(headers: Headers): ResH

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
    abstract class Response<Req, Res, ResH : HeaderProvider>(
        path: String,
        method: Method = Method.POST,
        requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
    ) : ApiContract<Req, Res, NoHeaders, ResH>(path, method, requiredResponseHeaders = requiredResponseHeaders) {
        context(_: Raise<Issue>)
        override fun decodeRequestHeaders(headers: Headers) = NoHeaders
    }

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
    ) : ApiContract<Req, Res, ReqH, NoHeaders>(path, method, requiredRequestHeaders = requiredRequestHeaders) {
        context(_: Raise<Issue>)
        override fun decodeResponseHeaders(headers: Headers) = NoHeaders
    }

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
    ) : ApiContract<Req, Res, NoHeaders, NoHeaders>(path, method) {
        context(_: Raise<Issue>)
        override fun decodeResponseHeaders(headers: Headers) = NoHeaders

        context(_: Raise<Issue>)
        override fun decodeRequestHeaders(headers: Headers) = NoHeaders
    }

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
    ) : ApiContract<Unit, Res, NoHeaders, NoHeaders>(path, method) {
        context(_: Raise<Issue>)
        override fun decodeResponseHeaders(headers: Headers) = NoHeaders

        context(_: Raise<Issue>)
        override fun decodeRequestHeaders(headers: Headers) = NoHeaders
    }
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
class SecurePingContract : ApiContract.Request<Unit, String, BearerAuth>("auth/ping", Method.GET, listOf(BearerAuth)) {
    context(_: Raise<Issue>)
    override fun decodeRequestHeaders(headers: Headers) = BearerAuth.decode(headers)
}
