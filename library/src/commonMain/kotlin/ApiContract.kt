package xyz.malefic.spyder

import kotlinx.serialization.Serializable

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param method The HTTP method to use for the API endpoint.
 * @param requiredFields The set of required header fields for the API endpoint.
 *
 * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
 * @param H The [HeaderProvider] type of the request headers. Use [NoHeaders] for no headers.
 */
abstract class ApiContract<Req, Res, H : HeaderProvider>(
    val path: String,
    val method: Method = Method.POST,
    val requiredFields: List<HeaderField> = emptyList(),
) {
    open val headers: Headers = Headers()
}

/**
 * A contract for the common "health" endpoint.
 */
class HealthContract : ApiContract<Unit, String, NoHeaders>("health", Method.GET)

/**
 * A contract for the common "ping" endpoint.
 */
class PingContract : ApiContract<Unit, String, NoHeaders>("ping", Method.GET)

/**
 * A contract for an example "auth/ping" endpoint.
 */
class SecurePingContract :
    ApiContract<Unit, String, BearerAuth>(
        path = "auth/ping",
        method = Method.GET,
        requiredFields = listOf(BearerAuth),
    )
