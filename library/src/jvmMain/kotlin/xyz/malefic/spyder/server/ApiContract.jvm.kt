package xyz.malefic.spyder.server

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.coroutines.runBlocking
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
import xyz.malefic.spyder.PaginatedResponse
import xyz.malefic.spyder.Pagination
import xyz.malefic.spyder.SpyderJson

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return a [Pair] in the format of `(response body, response headers)`.
 */
@JvmName("registerPair")
inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, Res, ReqH, ResH>.register(
    crossinline handler: suspend context(Raise<Issue>, ReqH) (Req) -> Pair<Res, ResH>,
): RoutingHttpHandler =
    "/api/$path" bind method.toHttp4k to { req ->
        runBlocking {
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
    }

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return the response body directly.
 */
inline fun <reified Req, reified Res, ReqH : HeaderProvider> ApiContract<Req, Res, ReqH, NoHeaders>.register(
    crossinline handler: suspend context(Raise<Issue>, ReqH) (Req) -> Res,
): RoutingHttpHandler =
    this.register<Req, Res, ReqH, NoHeaders> { req: Req -> handler(req) to NoHeaders } // TODO: Remove overloads by handling headers separately

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param handler The handler function for the route. Should return a [Pair] of the full list and response headers.
 */
@JvmName("registerPaginated")
inline fun <reified Req, reified T, ReqH : HeaderProvider, ResH : HeaderProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH>.register(
    crossinline handler: suspend context(Raise<Issue>, ReqH, Pagination) (Req) -> Pair<List<T>, ResH>,
): RoutingHttpHandler =
    "/api/$path" bind method.toHttp4k to { req ->
        runBlocking {
            val page = req.query("page")?.toIntOrNull() ?: 1
            val limit = req.query("limit")?.toIntOrNull() ?: 20

            val headers = Headers.fromPairs(req.headers)

            val pagination =
                object : Pagination {
                    override val page = page
                    override val limit = limit
                    override val offset = (page - 1) * limit
                    override var totalItems: Long? = null
                }

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
                        val (items, resH) = handler(this, decodeRequestHeaders(headers), pagination, body)
                        if (pagination.totalItems != null) {
                            PaginatedResponse.create(items, page, limit, pagination.totalItems!!) to resH
                        } else {
                            val total = items.size.toLong()
                            val start = pagination.offset.coerceIn(0, items.size)
                            val end = (start + limit).coerceAtMost(items.size)
                            PaginatedResponse.create(items.subList(start, end), page, limit, total) to resH
                        }
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
                        Response(Status.OK)
                            .body(SpyderJson.default.encodeToString(res))
                            .header("Content-Type", "application/json")

                    Headers.Builder().apply { add(resH) }.build().forEach { (k, v) ->
                        v.forEach { response = response.header(k, it) }
                    }

                    response
                }
            }
        }
    }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param handler The handler function for the route. Should return a [Pair] of the full list and response headers.
 */
@JvmName("registerPaginatedNoHeaders")
inline fun <reified Req, reified T, ReqH : HeaderProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, NoHeaders>.register(
    crossinline handler: suspend context(Raise<Issue>, ReqH, Pagination) (Req) -> List<T>,
): RoutingHttpHandler = this.register<Req, T, ReqH, NoHeaders> { req: Req -> handler(req) to NoHeaders }
