package xyz.malefic.spyder.client

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.xhr.FormData
import xyz.malefic.spyder.api.ApiResponse
import xyz.malefic.spyder.api.ApiContract
import xyz.malefic.spyder.core.HeaderProvider
import xyz.malefic.spyder.core.Headers
import xyz.malefic.spyder.core.PathProvider
import xyz.malefic.spyder.core.QueryProvider
import xyz.malefic.spyder.core.SpyderJson
import xyz.malefic.spyder.error.InternalIssue
import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.feature.multipart.Multipart

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest, myHeaders, myPath, myQuery) { set("Authorization", "...") }
 * ```
 */
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
            if (Req::class == Unit::class) {
                null
            } else if (request is Multipart) {
                val formData = FormData()
                request.fields.forEach { (k, v) -> formData.append(k, v) }
                request.files.forEach { (k, v) ->
                    val blob = Blob(arrayOf(v.bytes), BlobPropertyBag(type = v.contentType ?: "application/octet-stream"))
                    formData.append(k, blob, v.name)
                }
                formData
            } else {
                SpyderJson.default.encodeToString(request)
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

        val body =
            if (Res::class == Unit::class) {
                Unit as Res
            } else {
                catch({
                    SpyderJson.default.decodeFromString<Res>(text)
                }) {
                    raise(InternalIssue from it)
                }
            }

        ApiResponse(body, decodeResponseHeaders(Headers(rawHeaders)))
    }
