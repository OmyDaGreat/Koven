package xyz.malefic.spyder.server

import org.http4k.core.then
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.filter.debug
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Undertow
import org.http4k.server.asServer
import xyz.malefic.spyder.ApiContract

/**
 * Configuration for the [SpyderServer].
 */
data class SpyderConfig(
    var port: Int = 8080,
    var assetsPath: String = "assets",
    var corsPolicy: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive,
)

/**
 * A builder context for configuring routes.
 */
class SpyderServerBuilder(
    val config: SpyderConfig = SpyderConfig(),
) {
    private val routes = mutableListOf<RoutingHttpHandler>()

    fun add(route: RoutingHttpHandler) {
        routes += route
    }

    /**
     * Adds an [ApiContract] to the server by registering a route with the given [handler].
     *
     * @param contract The [ApiContract] to register.
     * @param handler The function to handle the request.
     */
    inline fun <reified Req, reified Res> handle(
        contract: ApiContract<Req, Res>,
        crossinline handler: (Req) -> Res,
    ) {
        add(contract.register(handler))
    }

    internal fun buildHandler(): RoutingHttpHandler =
        ServerFilters
            .Cors(config.corsPolicy)
            .then(routes(routes))
}

/**
 * A Spyder server instance.
 *
 * Usage example:
 * ```
 * val server = SpyderServer.create {
 *     config.port = 8081
 *     handle(PingContract) { "Pong" }
 * }.start()
 * ```
 */
class SpyderServer internal constructor(
    private val underlying: Http4kServer,
) {
    /**
     * Starts the server.
     */
    fun start(): SpyderServer = apply { underlying.start() }

    /**
     * Stops the server.
     */
    fun stop(): SpyderServer = apply { underlying.stop() }

    /**
     * Blocks the current thread until the server is stopped.
     */
    fun block(): SpyderServer = apply { underlying.block() }

    /**
     * Returns the port the server is listening on.
     */
    fun port(): Int = underlying.port()

    companion object {
        /**
         * DSL for creating and configuring a [SpyderServer] instance.
         *
         * @param httpConfig A function that configures the routes through [RoutingHttpHandler].
         * @param serverConfig A function that configures the server through [SpyderServerBuilder].
         */
        fun create(
            httpConfig: RoutingHttpHandler.() -> RoutingHttpHandler = { debug() },
            serverConfig: SpyderServerBuilder.() -> Unit,
        ): SpyderServer {
            val builder = SpyderServerBuilder().apply(serverConfig)
            val handler = builder.buildHandler().httpConfig()
            val server = handler.asServer(Undertow(builder.config.port))
            return SpyderServer(server)
        }
    }
}
