package xyz.malefic.spyder

import kotlinx.serialization.Serializable

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param method The HTTP method to use for the API endpoint.
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
    val requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
) {
    open val requestHeaders: Headers = Headers()
    open val responseHeaders: Headers = Headers()

    /**
     * Decodes the request headers into the type [ReqH].
     */
    abstract fun decodeRequestHeaders(headers: Headers): ReqH

    /**
     * Decodes the response headers into the type [ResH].
     */
    abstract fun decodeResponseHeaders(headers: Headers): ResH
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
class HealthContract : ApiContract<Unit, String, NoHeaders, NoHeaders>("health", Method.GET) {
    override fun decodeRequestHeaders(headers: Headers) = NoHeaders

    override fun decodeResponseHeaders(headers: Headers) = NoHeaders
}

/**
 * A contract for the common "ping" endpoint.
 */
class PingContract : ApiContract<Unit, String, NoHeaders, NoHeaders>("ping", Method.GET) {
    override fun decodeRequestHeaders(headers: Headers) = NoHeaders

    override fun decodeResponseHeaders(headers: Headers) = NoHeaders
}

/**
 * A contract for an example "auth/ping" endpoint.
 */
class SecurePingContract : ApiContract<Unit, String, BearerAuth, NoHeaders>("auth/ping", Method.GET, listOf(BearerAuth)) {
    override fun decodeRequestHeaders(headers: Headers) = BearerAuth.decode(headers)!! // TODO: Better error handling

    override fun decodeResponseHeaders(headers: Headers) = NoHeaders
}
