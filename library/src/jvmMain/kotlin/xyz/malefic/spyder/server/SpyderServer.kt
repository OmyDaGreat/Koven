package xyz.malefic.spyder.server

import arrow.core.raise.Raise
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.filter.debug
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.JettyLoom
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.HeaderProvider
import xyz.malefic.spyder.Issue
import xyz.malefic.spyder.NoHeaders
import xyz.malefic.spyder.PaginatedResponse
import xyz.malefic.spyder.Pagination
import xyz.malefic.spyder.PathProvider
import xyz.malefic.spyder.QueryProvider

/**
 * A builder context for configuring routes and server settings.
 */
class SpyderServerBuilder(
    val config: SpyderServerConfig,
) {
    private val routes = mutableListOf<RoutingHttpHandler>()

    fun add(route: RoutingHttpHandler) {
        routes += route
    }

    /**
     * Adds an [ApiContract] to the server by registering a route with the given [handler].
     *
     * @param handler The function to handle the request. Should return a [Pair] in the format of `(response body, response headers)`.
     */
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.handle(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Req) -> Pair<Res, ResH>,
    ) = add(register(handler))

    /**
     * Adds an [ApiContract] to the server by registering a route with the given [handler].
     *
     * @param handler The function to handle the request. Should return the response body directly.
     */
    @JvmName("handleNoResponseHeader")
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified Res, ReqH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, NoHeaders, PathP, QueryP>.handle(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP) (Req) -> Res,
    ) = add(register(handler))

    /**
     * Adds a paginated [ApiContract] to the server.
     *
     * @param handler The function to handle the request. Returns a [Pair] of the full list and response headers.
     */
    @JvmName("handlePaginated")
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified T, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP>.handle(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination) (Req) -> Pair<List<T>, ResH>,
    ) = add(register(handler))

    /**
     * Adds a paginated [ApiContract] to the server.
     *
     * @param handler The function to handle the request. Returns the full list of items.
     */
    @JvmName("handlePaginatedNoResponseHeader")
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified T, ReqH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, NoHeaders, PathP, QueryP>.handle(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination) (Req) -> List<T>,
    ) = add(register(handler))

    internal fun buildHandler(): RoutingHttpHandler =
        ServerFilters
            .Cors(config.corsPolicy)
            .then(routes(routes))
}

/**
 * Singleton Spyder server instance.
 *
 * Usage example:
 * ```
 * SpyderServer.start {
 *     config.port = 8081
 *     handle(PingContract) { "Pong" }
 * }.block()
 * ```
 */
object SpyderServer {
    val config = SpyderServerConfig()
    private var underlying: Http4kServer? = null

    val port get() = underlying?.port() ?: config.port

    /**
     * Configures and starts the server.
     *
     * @param httpConfig A function that configures the routes through [RoutingHttpHandler], akin to a [org.http4k.core.Filter].
     * @param serverConfig A function that configures the server through [SpyderServerBuilder].
     *
     * @throws IllegalStateException If the server is already running.
     */
    fun start(
        server: (Int) -> ServerConfig = { JettyLoom(it) },
        httpConfig: RoutingHttpHandler.() -> RoutingHttpHandler = { debug() },
        serverConfig: SpyderServerBuilder.() -> Unit = {},
    ): SpyderServer =
        apply {
            if (underlying == null) {
                val builder = SpyderServerBuilder(config).apply(serverConfig)
                val handler = builder.buildHandler().httpConfig()
                underlying = handler.asServer(server(config.port)).start()
            } else {
                error("Server is already running")
            }
        }

    /**
     * Stops the server.
     */
    fun stop(): SpyderServer =
        apply {
            underlying?.stop()
            underlying = null
        }

    /**
     * Blocks the current thread until the server is stopped.
     */
    fun block(): SpyderServer =
        apply {
            underlying?.block()
        }
}
