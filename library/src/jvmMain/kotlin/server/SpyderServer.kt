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
 * The global server instance.
 *
 * Example usage:
 * ```
 * fun main() {
 *     SpyderServer {
 *         PORT = 8081
 *         ASSETS_PATH = "data/storage"
 *         CORS_POLICY = CorsPolicy.UnsafeGlobalPermissive
 *
 *         handle(PingContract) { "Pong" }
 *     }.start()
 * }
 * ```
 */
@Suppress("ktlint:standard:property-naming")
object SpyderServer {
    private var underlying: Http4kServer? = null

    /**
     * Starts the server instance.
     *
     * @throws IllegalStateException if the server is already running.
     */
    fun start() {
        val server = underlying ?: error("SpyderServer not configured!")
        underlying = server.start()
    }

    /**
     * Stops the global server instance.
     */
    fun stop() {
        underlying?.stop()
        underlying = null
    }

    /**
     * DSL for basic configuration of the [SpyderServer] instance.
     */
    operator fun invoke(
        httpConfig: RoutingHttpHandler.() -> RoutingHttpHandler = { debug() },
        block: SpyderServer.() -> Unit,
    ): SpyderServer {
        block()
        return this(
            ServerFilters
                .Cors(CORS_POLICY)
                .then(routes(routes))
                .httpConfig()
                .asServer(Undertow(PORT)),
        )
    }

    /**
     * Sets the underlying server instance.
     */
    operator fun invoke(server: Http4kServer) = apply { underlying = server }

    /**
     * The port to listen on. Defaults to 8080.
     */
    var PORT: Int = 8080

    /**
     * The path to which assets are saved. Defaults to "assets".
     */
    var ASSETS_PATH: String = "assets"

    /**
     * The CORS policy to use. Defaults to [CorsPolicy.UnsafeGlobalPermissive].
     */
    var CORS_POLICY: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive

    private val routes = mutableListOf<RoutingHttpHandler>()

    /**
     * Registers a handler for the given [route].
     *
     * You should not use this directly unless you know what you're doing.
     */
    fun addRoute(route: RoutingHttpHandler) {
        routes += route
    }

    /**
     * Creates a route for the given [contract], attaching the given [handler].
     *
     * @param contract The [ApiContract] for the route.
     * @param handler A function that handles the request.
     */
    inline fun <reified Req, reified Res> handle(
        contract: ApiContract<Req, Res>,
        crossinline handler: (Req) -> Res,
    ) {
        addRoute(contract.register(handler))
    }
}
