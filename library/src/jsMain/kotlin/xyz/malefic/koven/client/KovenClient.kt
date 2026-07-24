package xyz.malefic.koven.client

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.varabyte.kobweb.browser.ApiFetcher
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.RequestBody
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.xhr.FormData
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.ApiContract
import xyz.malefic.koven.api.ApiResponse
import xyz.malefic.koven.api.HttpMethod
import xyz.malefic.koven.client.auth.AuthSession
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.error.InternalIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.multipart.Multipart

suspend fun ApiFetcher.call(
    httpMethod: HttpMethod,
    path: String,
    body: RequestBody? = null,
    headers: Headers = Headers(),
) = when (httpMethod) {
    HttpMethod.GET -> get(path, headers)
    HttpMethod.POST -> post(path, body, headers)
    HttpMethod.PUT -> put(path, body, headers)
    HttpMethod.DELETE -> delete(path, headers)
    HttpMethod.OPTIONS -> options(path, headers)
    HttpMethod.PATCH -> patch(path, body, headers)
    HttpMethod.HEAD -> head(path, headers)
}

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest, myHeaders, myPath, myQuery, myCookies) { set("Authorization", "...") }
 * ```
 *
 * @param request The request body.
 * @param headers The request headers.
 * @param pathParams The path parameters.
 * @param queryParams The query parameters.
 * @param cookies The request cookies.
 * @param headerBlock A block to add custom headers to the request.
 */
@Suppress("ktlint:standard:max-line-length", "UNCHECKED_CAST")
suspend inline fun <reified Req, reified Res, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.call(
    request: Req = Unit as Req,
    headers: ReqH = Empty as ReqH,
    pathParams: PathP = Empty as PathP,
    queryParams: QueryP = Empty as QueryP,
    cookies: CookieP = Empty as CookieP,
    crossinline headerBlock: Headers.Builder.() -> Unit = {},
): Either<Issue, ApiResponse<Res, ResH>> =
    either {
        val bodyData =
            when {
                Req::class == Unit::class -> {
                    null
                }

                request is Multipart -> {
                    val formData = FormData()
                    request.fields.forEach { (k, v) -> formData.append(k, v) }
                    request.files.forEach { (k, v) ->
                        val blob = Blob(arrayOf(v.bytes), BlobPropertyBag(type = v.contentType ?: "application/octet-stream"))
                        formData.append(k, blob, v.name)
                    }
                    formData
                }

                else -> {
                    requestFormat.encode(request)
                }
            }

        cookieDecoder.encodeCookies(cookies).forEach { Cookies.set(it) }

        var finalPath = path
        pathDecoder.encodePath(pathParams).forEach { (k, v) ->
            finalPath = finalPath.replace("{$k}", v)
        }

        val queryMap = queryDecoder.encodeQuery(queryParams)
        if (queryMap.isNotEmpty()) {
            val queryStr = queryMap.flatMap { (k, vs) -> vs.map { "$k=$it" } }.joinToString("&")
            finalPath = "$finalPath?$queryStr"
        }

        val response =
            catch({
                val body =
                    when (bodyData) {
                        is FormData -> bodyOf(bodyData)
                        is ByteArray -> bodyOf(bodyData, requestFormat.contentType)
                        else -> null
                    }
                window.api
                    .call(
                        httpMethod,
                        finalPath,
                        body,
                        Headers.build {
                            requestHeaderDecoder.encodeHeaders(headers).forEach { (k, vs) ->
                                vs.forEach { append(k, it) }
                            }
                            if (isProtected && AuthSession.isAuthenticated) {
                                set("Authorization", "Bearer ${AuthSession.accessToken}")
                            }
                            headerBlock()
                        },
                    )
            }) {
                raise(InternalIssue from it)
            }

        val responseBytes = Uint8Array(response.arrayBuffer().await()).unsafeCast<ByteArray>()

        val status = response.status.toInt()

        if (status >= 400) {
            raise(
                catch({ (responseFormat.serialization ?: KovenConfig.serialization).decodeIssue(responseBytes) })
                    { InternalIssue("Server error (${response.status}): ${responseBytes.decodeToString()}", status) },
            )
        }

        val rawHeaders = mutableMapOf<String, List<String>>()
        response.headers.asDynamic().forEach({ value: String, key: String ->
            rawHeaders[key.lowercase()] = listOf(value)
        })

        val headersObj = Headers(rawHeaders)

        val body =
            if (Res::class == Unit::class) {
                Unit as Res
            } else {
                catch({ responseFormat.decode(responseBytes, headersObj.getFirst("Content-Type") ?: responseFormat.contentType) })
                { raise(InternalIssue from it) }
            }

        ApiResponse(status, body, decodeResponseHeaders(headersObj))
    }
