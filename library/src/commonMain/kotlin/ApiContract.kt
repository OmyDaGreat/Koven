package xyz.malefic.spyder

import kotlinx.serialization.Serializable

/**
 * A contract for an API endpoint shared between the client and server.
 *
 * @param path The path of the API endpoint. Automatically prefixed with "api/".
 * @param method The HTTP method to use for the API endpoint.
 *
 * @param Req The [Serializable] type of the request body. If the request body is empty, this should be `Unit`.
 * @param Res The [Serializable] type of the request body. If the response body is empty, this should be `Unit`.
 */
abstract class ApiContract<Req, Res>(
    val path: String,
    val method: Method = Method.POST,
)
