package xyz.malefic.spyder.client

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.ApiResponse
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.InternalIssue
import xyz.malefic.spyder.Issue
import xyz.malefic.spyder.SpyderJson

/**
 * Extension to make calling contracts more ergonomic.
 *
 * Usage example:
 * ```
 * MyContract.call(myRequest, myHeaders) { set("Authorization", "...") }
 * ```
 */
suspend inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, Res, ReqH, ResH>.call(
    request: Req,
    headers: ReqH,
    crossinline headerBlock: Headers.Builder.() -> Unit = {},
): Either<Issue, ApiResponse<Res, ResH>> =
    either {
        val json =
            if (Req::class == Unit::class) {
                null
            } else {
                SpyderJson.default.encodeToString(request)
            }

        val response =
            catch({
                window.api
                    .call(
                        method,
                        path,
                        json?.let { bodyOf(json, "application/json") },
                        Headers.build {
                            add(this@call.requestHeaders)
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
