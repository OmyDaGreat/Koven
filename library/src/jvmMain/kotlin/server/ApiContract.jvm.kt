package xyz.malefic.spyder.server

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.BadRequestIssue
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.InternalIssue
import xyz.malefic.spyder.Issue
import xyz.malefic.spyder.NoHeaders
import xyz.malefic.spyder.SpyderJson

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return a [Pair] in the format of `(response body, response headers)`.
 */
inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, Res, ReqH, ResH>.register(
    crossinline handler: context(Raise<Issue>, ReqH) (Req) -> Pair<Res, ResH>,
): RoutingHttpHandler =
    path bind method.toHttp4k to { req ->
        val headers = Headers.fromPairs(req.headers)

        val result =
            either {
                val missing =
                    requiredRequestHeaders.filter {
                        headers[it].let { values -> values == null || values.all { value -> value.isBlank() } }
                    }
                ensure(missing.isEmpty()) { BadRequestIssue("Missing required header(s): ${missing.joinToString { it.field }}") }

                val body =
                    if (Req::class == Unit::class) {
                        Unit as Req
                    } else {
                        catch({
                            SpyderJson.default.decodeFromString<Req>(req.bodyString())
                        }) {
                            raise(BadRequestIssue("Invalid JSON for request body: ${it.message}"))
                        }
                    }

                catch({
                    handler(this, decodeRequestHeaders(headers), body)
                }) {
                    raise(InternalIssue from it)
                }
            }

        when (result) {
            is Either.Left -> {
                val issue = result.value
                val body = SpyderJson.default.encodeToString(Issue.serializer(), issue)
                Response(Status.fromCode(issue.status.toInt()) ?: Status.INTERNAL_SERVER_ERROR)
                    .body(body)
                    .header("Content-Type", "application/json")
            }

            is Either.Right -> {
                val (res, resH) = result.value
                var response =
                    if (Res::class == Unit::class) {
                        Response(Status.OK)
                    } else {
                        Response(Status.OK)
                            .body(SpyderJson.default.encodeToString(res))
                            .header("Content-Type", "application/json")
                    }

                Headers.Builder().apply { add(resH) }.build().forEach { (k, v) ->
                    v.forEach { response = response.header(k, it) }
                }

                response
            }
        }
    }

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return the response body directly.
 */
inline fun <reified Req, reified Res, ReqH : HeaderProvider> ApiContract<Req, Res, ReqH, NoHeaders>.register(
    crossinline handler: context(Raise<Issue>, ReqH) (Req) -> Res,
): RoutingHttpHandler = this.register<Req, Res, ReqH, NoHeaders> { req: Req -> handler(req) to NoHeaders }
