package xyz.malefic.spyder.server

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.http4k.core.MultipartEntity
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.multipartIterator
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.BadRequestIssue
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Headers
import xyz.malefic.spyder.InternalIssue
import xyz.malefic.spyder.Issue
import xyz.malefic.spyder.Multipart
import xyz.malefic.spyder.NoHeaders
import xyz.malefic.spyder.PaginatedResponse
import xyz.malefic.spyder.Pagination
import xyz.malefic.spyder.PathProvider
import xyz.malefic.spyder.QueryProvider
import xyz.malefic.spyder.SpyderJson

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return a [Pair] in the format of `(response body, response headers)`.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Req) -> Pair<Res, ResH>,
): RoutingHttpHandler =
    baseRegister { req, reqH, pathP, queryP ->
        val body = decodeBody<Req>(req)
        handler(this, reqH, pathP, queryP, body)
    }

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return the response body directly.
 */
@JvmName("registerNoResponseHeader")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, NoHeaders, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Req) -> Res,
): RoutingHttpHandler = register<Req, Res, ReqH, NoHeaders, PathP, QueryP> { req: Req -> handler(req) to NoHeaders }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param handler The handler function for the route. Should return a [Pair] in the format of `(response list, response headers)`.
 */
@JvmName("registerPaginated")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified T, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination) (Req) -> Pair<List<T>, ResH>,
): RoutingHttpHandler =
    baseRegister { req, reqH, pathP, queryP ->
        val page = req.query("page")?.toIntOrNull() ?: 1
        val limit = req.query("limit")?.toIntOrNull() ?: 20

        val pagination =
            object : Pagination {
                override val page = page
                override val limit = limit
                override val offset = (page - 1) * limit
                override var totalItems: Long? = null
            }

        val body = decodeBody<Req>(req)
        val (items, resH) = handler(this, reqH, pathP, queryP, pagination, body)

        val response =
            if (pagination.totalItems != null) {
                PaginatedResponse.create(items, page, limit, pagination.totalItems!!)
            } else {
                val total = items.size.toLong()
                val start = pagination.offset.coerceIn(0, items.size)
                val end = (start + limit).coerceAtMost(items.size)
                PaginatedResponse.create(items.subList(start, end), page, limit, total)
            }
        response to resH
    }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param handler The handler function for the route. Should return a [Pair] of the full list and response headers.
 */
@JvmName("registerPaginatedNoResponseHeader")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified T, ReqH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, NoHeaders, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination) (Req) -> List<T>,
): RoutingHttpHandler = register<Req, T, ReqH, NoHeaders, PathP, QueryP> { req: Req -> handler(req) to NoHeaders }

/**
 * Creates a route for the given [ApiContract] with a multipart request body.
 *
 * @param handler The handler function for the route. Should return a [Pair] in the format of `(response body, response headers)`.
 */
@JvmName("registerMultipart")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Multipart) -> Pair<Res, ResH>,
): RoutingHttpHandler = register<Multipart, Res, ReqH, ResH, PathP, QueryP>(handler)

/**
 * Creates a route for the given [ApiContract] with a multipart request body.
 *
 * @param handler The handler function for the route. Should return the response body directly.
 */
@JvmName("registerMultipartNoResponseHeader")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Res, ReqH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Multipart, Res, ReqH, NoHeaders, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Multipart) -> Res,
): RoutingHttpHandler = register<Res, ReqH, NoHeaders, PathP, QueryP> { req: Multipart -> handler(req) to NoHeaders }

@PublishedApi
internal inline fun <reified Req> Raise<Issue>.decodeBody(req: Request): Req =
    if (Req::class == Unit::class) {
        Unit as Req
    } else if (Req::class == Multipart::class) {
        catch({
            val fields = mutableMapOf<String, String>()
            val files = mutableMapOf<String, Multipart.File>()

            req.multipartIterator().forEach { part ->
                when (part) {
                    is MultipartEntity.Field -> {
                        fields[part.name] = part.value
                    }

                    is MultipartEntity.File -> {
                        files[part.name] =
                            Multipart.File(
                                part.file.filename,
                                part.file.contentType.value,
                                part.file.content.readAllBytes(),
                            )
                    }
                }
            }
            Multipart(fields, files) as Req
        }) { raise(BadRequestIssue("Invalid multipart request: ${it.message}")) }
    } else {
        catch({
            SpyderJson.default.decodeFromString<Req>(req.bodyString())
        }) { raise(BadRequestIssue("Invalid JSON for request body: ${it.message}")) }
    }

@PublishedApi
@Suppress("ktlint:standard:max-line-length")
internal inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.baseRegister(
    crossinline logic: Raise<Issue>.(req: Request, reqH: ReqH, pathP: PathP, queryP: QueryP) -> Pair<Res, ResH>,
): RoutingHttpHandler =
    "/api/$path" bind method.toHttp4k to { req ->
        val headers = Headers.fromPairs(req.headers)
        val pathParams = "\\{([^}]+)\\}".toRegex().findAll(path).map { it.groupValues[1] }.associateWith { req.path(it) ?: "" }
        val queryMap = queryParams.associateWith { req.queries(it).map { v -> v ?: "" } }

        val result =
            either {
                val missing =
                    requiredRequestHeaders.filter {
                        headers[it].let { values -> values == null || values.all { value -> value.isBlank() } }
                    }
                ensure(missing.isEmpty()) { BadRequestIssue("Missing required header(s): ${missing.joinToString { it.field }}") }

                catch({ logic(this, req, decodeRequestHeaders(headers), decodePath(pathParams), decodeQuery(queryMap)) }) {
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
