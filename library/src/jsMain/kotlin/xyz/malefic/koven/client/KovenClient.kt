package xyz.malefic.koven.client

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.varabyte.kobweb.browser.api
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
import xyz.malefic.koven.client.auth.AuthSession
import xyz.malefic.koven.core.HeaderProvider
import xyz.malefic.koven.core.Headers
import xyz.malefic.koven.core.PathProvider
import xyz.malefic.koven.core.QueryProvider
import xyz.malefic.koven.error.InternalIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.multipart.Multipart

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest, myHeaders, myPath, myQuery) { set("Authorization", "...") }
 * ```
 *
 * @param request The request body.
 * @param headers The request headers.
 * @param pathParams The path parameters.
 * @param queryParams The query parameters.
 * @param headerBlock A block to add custom headers to the request.
 */
@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("ktlint:standard:max-line-length")
suspend inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.call(
    request: Req,
    headers: ReqH,
    pathParams: PathP,
    queryParams: QueryP,
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

        var finalPath = path
        pathParams.providePath().forEach { (k, v) ->
            finalPath = finalPath.replace("{$k}", v)
        }

        val queryMap = queryParams.provideQuery()
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
                            add(headers)
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

        if (!response.ok) {
            raise(
                catch({
                    (responseFormat.serialization ?: KovenConfig.serialization).decodeIssue(responseBytes)
                })
                    { InternalIssue("Server error (${response.status}): ${responseBytes.decodeToString()}", response.status) },
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
                catch({
                    responseFormat.decode(responseBytes, headersObj.getFirst("Content-Type") ?: responseFormat.contentType)
                }) { raise(InternalIssue from it) }
            }

        ApiResponse(body, decodeResponseHeaders(headersObj))
    }
