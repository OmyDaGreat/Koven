package xyz.malefic.koven.server

import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.context.raise
import arrow.core.raise.either
import org.http4k.core.Filter
import org.http4k.core.MemoryBody
import org.http4k.core.MultipartEntity
import org.http4k.core.NoOp
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.http4k.core.multipartIterator
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.ApiContract
import xyz.malefic.koven.api.ApiResponse
import xyz.malefic.koven.api.ApiResponse.Companion.with
import xyz.malefic.koven.core.field.Cookie
import xyz.malefic.koven.core.field.Cookies
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.core.field.KovenPair
import xyz.malefic.koven.core.field.PathField.Companion.PATH_PARAM_REGEX
import xyz.malefic.koven.core.field.PathParams
import xyz.malefic.koven.core.field.QueryParams
import xyz.malefic.koven.core.field.flattenPair
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.InternalIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.server.OAuthHandler
import xyz.malefic.koven.feature.auth.server.PasswordAuthHandler
import xyz.malefic.koven.feature.multipart.Multipart
import xyz.malefic.koven.feature.pagination.PaginatedResponse
import xyz.malefic.koven.feature.pagination.Pagination
import kotlin.uuid.Uuid

/**
 * Creates a route for the given [ApiContract].
 *
 * The handler function can return:
 * - [ApiResponse] for a full response with body, headers, and cookies.
 * - [Res] for a simple response body.
 * - [Headers] for a response with only headers.
 * - [Cookies] or [Cookie] for a response with only cookies.
 * - [Unit] for an empty response.
 *
 * Context parameters in the [handler] can be accessed via [contextOf].
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@Suppress("UNCHECKED_CAST", "ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Raise<Issue>, ReqH, CookieP, Principal) Request.(Req, PathP, QueryP) -> Any?,
): RoutingHttpHandler =
    baseRegister(filter) { req, reqH, pathP, queryP, cookieP ->
        val principal = authenticate(req)
        val body = decodeBody(req)

        val result =
            context(principal, reqH, cookieP) {
                req.handler(body, pathP, queryP)
            }

        val flattened = if (result is KovenPair<*, *>) result.flattenPair() else listOf(result)

        var responseStatus = 200
        var responseBody: Res? = null
        var responseHeaders = Headers()
        var responseCookies = Cookies()
        var responseQueryParams = QueryParams()
        var responsePathParams = PathParams()

        var bodySet = false

        flattened.forEach { item ->
            when (item) {
                is ApiResponse<*, *> -> {
                    responseStatus = item.status
                    responseBody = item.body as? Res
                    responseHeaders += item.headers as? Headers ?: Headers()
                    responseCookies += Cookies(item.cookies)
                    bodySet = true
                }

                is Headers -> {
                    responseHeaders += item
                }

                is Cookies -> {
                    responseCookies += item
                }

                is Cookie -> {
                    responseCookies += Cookies(listOf(item))
                }

                is QueryParams -> {
                    responseQueryParams += item
                }

                is PathParams -> {
                    responsePathParams += item
                }

                is List<*> -> {
                    if (item.firstOrNull() is Cookie) {
                        responseCookies += Cookies(item as List<Cookie>)
                    } else if (!bodySet) {
                        responseBody = item as? Res
                        bodySet = true
                    }
                }

                else -> {
                    if (!bodySet && item != null && item != Unit) {
                        responseBody = item as? Res
                        bodySet = true
                    }
                }
            }
        }

        if (responseBody == null && Res::class == Unit::class) {
            responseBody = Unit as Res
        }

        ApiResponse(responseStatus, responseBody!!, responseHeaders as ResH, responseCookies.list)
    }

/**
 * Simplifies registration for [ApiContract] types with no path or query parameters.
 */
@JvmName("registerSimple")
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH, ResH, CookieP> ApiContract<Req, Res, ReqH, ResH, Empty, Empty, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Raise<Issue>, ReqH, CookieP, Principal) Request.(Req) -> Any?,
): RoutingHttpHandler = register(filter) { req, _, _ -> handler(this, req) }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * The handler function can return:
 * - [ApiResponse] containing a `List<T>`.
 * - `List<T>` directly.
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@Suppress("UNCHECKED_CAST", "ktlint:standard:max-line-length")
inline fun <reified Req, reified T, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP, CookieP>.registerPaginated(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Raise<Issue>, ReqH, CookieP, Principal) Request.(Req, PathP, QueryP, Pagination) -> Any?,
): RoutingHttpHandler =
    baseRegister(filter) { req, reqH, pathP, queryP, cookieP ->
        val principal = authenticate(req)
        val page = req.query("page")?.toIntOrNull() ?: 1
        val limit = req.query("limit")?.toIntOrNull() ?: 20

        val pagination =
            object : Pagination {
                override val page = page
                override val limit = limit
                override val offset = (page - 1) * limit
                override var totalItems: Long? = null
            }

        val body = decodeBody(req)
        val result =
            context(principal, reqH, cookieP) {
                req.handler(body, pathP, queryP, pagination)
            }

        val (items, resH) =
            when (result) {
                is ApiResponse<*, *> -> (result.body as List<T>) to (result.headers as ResH)
                else -> (result as List<T>) to decodeResponseHeaders(Headers())
            }

        val response =
            if (pagination.totalItems != null) {
                PaginatedResponse.create(items, page, limit, pagination.totalItems!!)
            } else {
                val total = items.size.toLong()
                val start = pagination.offset.coerceIn(0, items.size)
                val end = (start + limit).coerceAtMost(items.size)
                PaginatedResponse.create(items.subList(start, end), page, limit, total)
            }
        response with resH
    }

/**
 * Creates a route for the given [ApiContract] with a multipart request body.
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Res, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP, CookieP>.registerMultipart(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Raise<Issue>, ReqH, CookieP, Principal) Request.(Multipart, PathP, QueryP) -> Any?,
): RoutingHttpHandler = register<Multipart, Res, ReqH, ResH, PathP, QueryP, CookieP>(filter, handler)

@PublishedApi
context(_: Raise<Issue>)
internal fun ApiContract<*, *, *, *, *, *, *>.authenticate(req: Request): Principal {
    if (!isProtected || KovenConfig.auth == AuthType.NoAuth) return anonymousPrincipal

    return when (val auth = KovenConfig.auth) {
        is AuthType.NoAuth -> anonymousPrincipal
        is AuthType.Password -> context(auth) { PasswordAuthHandler.authenticate(req) }
        is AuthType.OAuth -> context(auth) { OAuthHandler.authenticate(req) }
    }
}

@PublishedApi
internal val anonymousPrincipal =
    object : Principal {
        override val userId: Uuid = Uuid.NIL
        override val username: String = "anonymous"
    }

@PublishedApi
@Suppress("ktlint:standard:max-line-length")
context(r: Raise<Issue>)
internal inline fun <reified Req, reified Res, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.decodeBody(
    req: Request,
): Req =
    when (Req::class) {
        Unit::class -> {
            Unit as Req
        }

        Multipart::class -> {
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
        }

        else -> {
            catch({ requestFormat.decode(req.body.payload.array(), req.header("Content-Type") ?: requestFormat.contentType) })
            { raise(BadRequestIssue("Invalid body: ${it.message}")) }
        }
    }

@PublishedApi
@Suppress("ktlint:standard:max-line-length")
internal inline fun <reified Req, reified Res, ReqH, ResH, PathP, QueryP, CookieP> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.baseRegister(
    filter: Filter = Filter.NoOp,
    crossinline logic: Raise<Issue>.(req: Request, reqH: ReqH, pathP: PathP, queryP: QueryP, cookieP: CookieP) -> ApiResponse<Res, ResH>,
): RoutingHttpHandler {
    val pathParamNames = PATH_PARAM_REGEX.findAll(path).map { it.groupValues[1] }.toList()
    return filter.then(
        "/${KovenConfig.apiPrefix}/$path" bind httpMethod.toHttp4k to { req ->
            val headers = Headers.fromPairs(req.headers)
            val pathParams = pathParamNames.associateWith { req.path(it) ?: "" }
            val queryMap = queryDecoder.fields.associateWith { req.queries(it).map { v -> v ?: "" } }

            val result =
                either {
                    val reqCookies = req.cookies().associate { it.name to it.value }

                    val reqH = decodeRequestHeaders(headers)
                    val pathP = decodePath(pathParams)
                    val queryP = decodeQuery(QueryParams(queryMap))
                    val cookieP = decodeCookies(reqCookies)

                    catch({ logic(this, req, reqH, pathP, queryP, cookieP) })
                    { raise(InternalIssue from it) }
                }

            result.fold(
                { issue ->
                    val serialization = responseFormat.serialization ?: KovenConfig.serialization
                    val body = serialization.encodeIssue(issue)
                    Response(Status.fromCode(issue.status) ?: Status.INTERNAL_SERVER_ERROR)
                        .body(MemoryBody(body))
                        .header("Content-Type", serialization.contentType)
                },
                { response ->
                    val (status, res, resH, cookies) = response
                    var response =
                        when {
                            Res::class == Unit::class -> {
                                Response(Status.fromCode(status) ?: Status.INTERNAL_SERVER_ERROR)
                            }

                            else -> {
                                Response(Status.fromCode(status) ?: Status.INTERNAL_SERVER_ERROR)
                                    .body(MemoryBody(responseFormat.encode(res)))
                                    .header("Content-Type", responseFormat.contentType)
                            }
                        }

                    Headers
                        .build {
                            responseHeaderDecoder.encodeHeaders(resH).forEach { (k, v) ->
                                v.forEach { append(k, it) }
                            }
                        }.forEach { (k, v) ->
                            v.forEach { response = response.header(k, it) }
                        }

                    cookies.forEach { cookie ->
                        response = response.cookie(cookie)
                    }

                    response
                },
            )
        },
    )
}
