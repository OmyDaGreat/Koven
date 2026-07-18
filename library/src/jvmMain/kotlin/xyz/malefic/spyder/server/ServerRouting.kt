package xyz.malefic.spyder.server

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.http4k.core.MemoryBody
import org.http4k.core.MultipartEntity
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.multipartIterator
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import xyz.malefic.spyder.SpyderConfig
import xyz.malefic.spyder.api.ApiContract
import xyz.malefic.spyder.api.ApiResponse
import xyz.malefic.spyder.api.ApiResponse.Companion.with
import xyz.malefic.spyder.core.CookieField
import xyz.malefic.spyder.core.HeaderProvider
import xyz.malefic.spyder.core.Headers
import xyz.malefic.spyder.core.PathProvider
import xyz.malefic.spyder.core.QueryProvider
import xyz.malefic.spyder.core.SameSite
import xyz.malefic.spyder.error.BadRequestIssue
import xyz.malefic.spyder.error.InternalIssue
import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.feature.auth.AuthType
import xyz.malefic.spyder.feature.auth.Principal
import xyz.malefic.spyder.feature.auth.server.OAuthHandler
import xyz.malefic.spyder.feature.auth.server.PasswordAuthHandler
import xyz.malefic.spyder.feature.multipart.Multipart
import xyz.malefic.spyder.feature.pagination.PaginatedResponse
import xyz.malefic.spyder.feature.pagination.Pagination
import kotlin.uuid.Uuid
import org.http4k.core.cookie.Cookie as Http4kCookie
import org.http4k.core.cookie.SameSite as Http4kSameSite

/**
 * Gets a cookie from the [Request] by its [field].
 */
context(_: Raise<Issue>)
operator fun <T> Request.get(field: CookieField<T>): T = field.decode(cookies().associate { it.name to it.value })

/**
 * Creates a route for the given [ApiContract].
 *
 * @param handler The handler function for the route. Should return an [ApiResponse] in the format of `(response body, response headers)`.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.register(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Principal, Request) (Request, Req) -> ApiResponse<Res, ResH>,
): RoutingHttpHandler =
    baseRegister { req, reqH, pathP, queryP ->
        val principal = authenticate(req)
        val body = decodeBody(req)
        handler(this, reqH, pathP, queryP, principal, req, req, body)
    }

/**
 * Creates a route for the given [ApiContract] that returns a paginated response with a [Pagination] context.
 *
 * The route will automatically handle `page` and `limit` query parameters to slice the list. If the [Pagination.totalItems] value provided in context is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
 *
 * @param handler The handler function for the route. Should return an [ApiResponse] in the format of `(response list, response headers)`.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Req, reified T, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP>.registerPaginated(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination, Principal, Request) (
        Request,
        Req,
    ) -> ApiResponse<List<T>, ResH>,
): RoutingHttpHandler =
    baseRegister { req, reqH, pathP, queryP ->
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
        val (items, resH) = handler(this, reqH, pathP, queryP, pagination, principal, req, req, body)

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
 * @param handler The handler function for the route. Should return an [ApiResponse] in the format of `(response body, response headers)`.
 */
@Suppress("ktlint:standard:max-line-length")
inline fun <reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP>.registerMultipart(
    crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Principal, Request) (Request, Multipart) -> ApiResponse<Res, ResH>,
): RoutingHttpHandler = register<Multipart, Res, ReqH, ResH, PathP, QueryP>(handler)

@PublishedApi
context(_: Raise<Issue>)
internal fun ApiContract<*, *, *, *, *, *>.authenticate(req: Request): Principal {
    if (!isProtected || SpyderConfig.auth == AuthType.NoAuth) return anonymousPrincipal

    return when (val auth = SpyderConfig.auth) {
        is AuthType.NoAuth -> anonymousPrincipal
        is AuthType.Password -> PasswordAuthHandler.authenticate(req)
        is AuthType.OAuth -> OAuthHandler.authenticate(req)
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
internal inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.decodeBody(
    req: Request,
): Req =
    with(r) {
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
                catch({
                    requestFormat.decode(req.body.payload.array(), req.header("Content-Type") ?: requestFormat.contentType)
                }) { raise(BadRequestIssue("Invalid body: ${it.message}")) }
            }
        }
    }

@PublishedApi
@Suppress("ktlint:standard:max-line-length")
internal inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.baseRegister(
    crossinline logic: Raise<Issue>.(req: Request, reqH: ReqH, pathP: PathP, queryP: QueryP) -> ApiResponse<Res, ResH>,
): RoutingHttpHandler =
    "/${SpyderConfig.apiPrefix}/$path" bind httpMethod.toHttp4k to { req ->
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
                val serialization = responseFormat.serialization ?: SpyderConfig.serialization
                val body = serialization.encodeIssue(issue)
                Response(Status.fromCode(issue.status.toInt()) ?: Status.INTERNAL_SERVER_ERROR)
                    .body(MemoryBody(body))
                    .header("Content-Type", serialization.contentType)
            }

            is Either.Right -> {
                val (res, resH, cookies) = result.value
                var response =
                    when {
                        Res::class == Unit::class -> {
                            Response(Status.OK)
                        }

                        else -> {
                            Response(Status.OK)
                                .body(MemoryBody(responseFormat.encode(res)))
                                .header("Content-Type", responseFormat.contentType)
                        }
                    }

                Headers.Builder().apply { add(resH) }.build().forEach { (k, v) ->
                    v.forEach { response = response.header(k, it) }
                }

                cookies.flatMap { it.provide() }.forEach { cookie ->
                    response =
                        response.cookie(
                            Http4kCookie(
                                name = cookie.name,
                                value = cookie.value,
                                maxAge = cookie.maxAge,
                                path = cookie.path,
                                domain = cookie.domain,
                                secure = cookie.secure,
                                httpOnly = cookie.httpOnly,
                                sameSite =
                                    when (cookie.sameSite) {
                                        SameSite.Strict -> Http4kSameSite.Strict
                                        SameSite.Lax -> Http4kSameSite.Lax
                                        SameSite.None -> Http4kSameSite.None
                                    },
                            ),
                        )
                }

                response
            }
        }
    }
