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
import xyz.malefic.koven.auth.AuthType
import xyz.malefic.koven.auth.Principal
import xyz.malefic.koven.auth.server.ApiKeyAuthHandler
import xyz.malefic.koven.auth.server.OAuthHandler
import xyz.malefic.koven.auth.server.PasswordAuthHandler
import xyz.malefic.koven.contract.ApiContract
import xyz.malefic.koven.contract.ApiResponse
import xyz.malefic.koven.contract.field.Empty
import xyz.malefic.koven.contract.field.Headers
import xyz.malefic.koven.contract.field.PathField.Companion.PATH_PARAM_REGEX
import xyz.malefic.koven.contract.field.QueryParams
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.InternalIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.multipart.Multipart
import xyz.malefic.koven.pagination.PaginatedResponse
import xyz.malefic.koven.pagination.Pagination
import xyz.malefic.koven.util.sanitizeFilename
import kotlin.uuid.Uuid

/**
 * Creates a route for the given [ApiContract].
 *
 * Context parameters in the [handler] can be accessed via [contextOf].
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@OverloadResolutionByLambdaReturnType
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH, reified ResH, PathP, QueryP, CookieP> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>.(Req, PathP, QueryP)
    -> ApiResponse<Res, ResH>,
): RoutingHttpHandler =
    baseRegister(filter) { req, reqH, pathP, queryP, cookieP ->
        val principal = authenticate(req)
        val body = decodeBody(req)

        context(req, principal, reqH, cookieP) {
            this@register.handler(body, pathP, queryP)
        }
    }

/**
 * Overload for [register] that allows returning [Unit] when the response body is [Unit] and headers are [Empty].
 */
@OverloadResolutionByLambdaReturnType
@JvmName("registerUnit")
inline fun <reified Req, ReqH, PathP, QueryP, CookieP> ApiContract<Req, Unit, ReqH, Empty, PathP, QueryP, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Req, Unit, ReqH, Empty, PathP, QueryP, CookieP>.(Req, PathP, QueryP)
    -> Unit,
): RoutingHttpHandler =
    register<Req, Unit, ReqH, Empty, PathP, QueryP, CookieP>(filter) { req, path, query ->
        handler(req, path, query)
        ApiResponse(Unit, Empty)
    }

/**
 * Simplifies registration for [ApiContract] types with no path or query parameters.
 */
@OverloadResolutionByLambdaReturnType
@JvmName("registerSimple")
inline fun <reified Req, reified Res, ReqH, reified ResH, CookieP> ApiContract<Req, Res, ReqH, ResH, Empty, Empty, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Req, Res, ReqH, ResH, Empty, Empty, CookieP>.(Req)
    -> ApiResponse<Res, ResH>,
): RoutingHttpHandler =
    baseRegister(filter) { req, reqH, _, _, cookieP ->
        val principal = authenticate(req)
        val body = decodeBody(req)

        context(req, principal, reqH, cookieP) {
            this@register.handler(body)
        }
    }

/**
 * Overload for [register] (simple) that allows returning [Unit] when the response body is [Unit] and headers are [Empty].
 */
@JvmName("registerSimpleUnit")
inline fun <reified Req, ReqH, CookieP> ApiContract<Req, Unit, ReqH, Empty, Empty, Empty, CookieP>.register(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Req, Unit, ReqH, Empty, Empty, Empty, CookieP>.(Req)
    -> Unit,
): RoutingHttpHandler =
    register<Req, Unit, ReqH, Empty, CookieP>(filter) { req ->
        context(contextOf<Request>(), contextOf<Raise<Issue>>(), contextOf<ReqH>(), contextOf<CookieP>(), contextOf<Principal>()) {
            handler(req)
        }
        ApiResponse(Unit, Empty)
    }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified T, ReqH, reified ResH, PathP, QueryP, CookieP> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP, CookieP>.registerPaginated(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP, CookieP>.(Req, PathP, QueryP, Pagination)
    -> ApiResponse<List<T>, ResH>,
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
            context(req, principal, reqH, cookieP) {
                this@registerPaginated.handler(body, pathP, queryP, pagination)
            }

        val items = result.body
        val resH = result.headers

        val responseBody =
            if (pagination.totalItems != null) {
                PaginatedResponse.create(items, page, limit, pagination.totalItems!!)
            } else {
                val total = items.size.toLong()
                val start = pagination.offset.coerceIn(0, items.size)
                val end = (start + limit).coerceAtMost(items.size)
                PaginatedResponse.create(items.subList(start, end), page, limit, total)
            }

        ApiResponse(result.status, responseBody, resH, result.cookies)
    }

/**
 * Creates a route for the given [ApiContract] with a multipart request body.
 *
 * @param filter The filter to apply to the route.
 * @param handler The handler function for the route.
 */
@OverloadResolutionByLambdaReturnType
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Res, ReqH, reified ResH, PathP, QueryP, CookieP> ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP, CookieP>.registerMultipart(
    filter: Filter = Filter.NoOp,
    crossinline handler: context(Request, Raise<Issue>, ReqH, CookieP, Principal)
    ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP, CookieP>.(Multipart, PathP, QueryP)
    -> ApiResponse<Res, ResH>,
): RoutingHttpHandler = register<Multipart, Res, ReqH, ResH, PathP, QueryP, CookieP>(filter, handler)

@PublishedApi
context(_: Raise<Issue>)
internal fun ApiContract<*, *, *, *, *, *, *>.authenticate(req: Request): Principal {
    val auth = KovenConfig.auth

    if (!isProtected || auth == AuthType.NoAuth) return anonymousPrincipal

    return when (auth) {
        is AuthType.Password -> context(auth) { PasswordAuthHandler.authenticate(req) }
        is AuthType.OAuth -> context(auth) { OAuthHandler.authenticate(req) }
        is AuthType.ApiKey -> context(auth) { ApiKeyAuthHandler.authenticate(req) }
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
                                    part.file.filename.sanitizeFilename(),
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
