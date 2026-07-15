package xyz.malefic.spyder.client

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
import xyz.malefic.spyder.api.ApiContract
import xyz.malefic.spyder.api.ApiResponse
import xyz.malefic.spyder.core.HeaderProvider
import xyz.malefic.spyder.core.Headers
import xyz.malefic.spyder.core.PathProvider
import xyz.malefic.spyder.core.QueryProvider
import xyz.malefic.spyder.core.SpyderJson
import xyz.malefic.spyder.error.InternalIssue
import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.feature.body.CustomBody
import xyz.malefic.spyder.feature.body.SpyderRawBody
import xyz.malefic.spyder.feature.body.binary.BinaryBody
import xyz.malefic.spyder.feature.body.multipart.Multipart
import xyz.malefic.spyder.feature.body.text.PlainTextBody
import xyz.malefic.spyder.feature.body.xml.XmlBody

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

                request is SpyderRawBody -> {
                    request
                }

                else -> {
                    SpyderJson.default.encodeToString(request)
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
                        is String -> bodyOf(bodyData, "application/json")
                        is SpyderRawBody -> bodyOf(bodyData.encode(), bodyData.contentType)
                        else -> null
                    }
                window.api
                    .call(
                        method,
                        finalPath,
                        body,
                        Headers.build {
                            add(headers)
                            headerBlock()
                        },
                    )
            }) {
                raise(InternalIssue from it)
            }

        val text = response.text().await()

        if (!response.ok) {
            val issue =
                catch({ SpyderJson.default.decodeFromString<Issue>(text) })
                { InternalIssue("Server error (${response.status}): $text", response.status) }
            raise(issue)
        }

        val rawHeaders = mutableMapOf<String, List<String>>()
        response.headers.asDynamic().forEach({ value: String, key: String ->
            rawHeaders[key.lowercase()] = listOf(value)
        })

        val headersObj = Headers(rawHeaders)

        val body =
            when (Res::class) {
                Unit::class -> {
                    Unit as Res
                }

                BinaryBody::class -> {
                    val bytes = Uint8Array(response.arrayBuffer().await()).unsafeCast<ByteArray>()
                    BinaryBody(bytes, headersObj.getFirst("Content-Type") ?: "application/octet-stream") as Res
                }

                PlainTextBody::class -> {
                    PlainTextBody(response.text().await()) as Res
                }

                XmlBody::class -> {
                    XmlBody(response.text().await()) as Res
                }

                CustomBody::class, SpyderRawBody::class -> {
                    CustomBody(
                        response.text().await(),
                        headersObj.getFirst("Content-Type") ?: "text/plain",
                    ) as Res
                }

                else -> {
                    catch({ SpyderJson.default.decodeFromString<Res>(response.text().await()) }) { raise(InternalIssue from it) }
                }
            }

        ApiResponse(body, decodeResponseHeaders(headersObj))
    }
