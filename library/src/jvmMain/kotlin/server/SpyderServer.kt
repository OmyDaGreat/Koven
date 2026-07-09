package xyz.malefic.spyder.server

import org.http4k.core.then
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Undertow
import org.http4k.server.asServer
import xyz.malefic.spyder.ApiContract

/**
 * A wrapper for an http4k [Http4kServer]. It delegates all operations to the underlying server.
 */
class SpyderServer(
    private val server: Http4kServer,
) : Http4kServer by server

/**
 * A builder for the [SpyderServer].
 */
class SpyderServerBuilder(
    var port: Int = 8080,
) {
    val handlers = mutableListOf<RoutingHttpHandler>()

    /**
     * Registers a handler for the given [contract].
     */
    inline fun <reified Req, reified Res> handle(
        contract: ApiContract<Req, Res>,
        crossinline handler: (Req) -> Res,
    ) {
        handlers += contract.register(handler)
    }

    /**
     * Builds the [SpyderServer].
     */
    fun build(): SpyderServer =
        SpyderServer(
            ServerFilters
                .Cors(CorsPolicy.UnsafeGlobalPermissive)
                .then(routes(handlers))
                .asServer(Undertow(port)),
        )
}

/**
 * Creates and configures a [SpyderServer] using a DSL.
 *
 * Example usage:
 * ```
 * spyderServer(8080) {
 *     handle(PingContract) { req ->
 *         PongResponse("Hello!")
 *     }
 * }.start()
 * ```
 *
 * @param port The port to run the server on.
 * @param block The configuration block.
 */
fun spyderServer(
    port: Int = 8080,
    block: SpyderServerBuilder.() -> Unit,
): SpyderServer = SpyderServerBuilder(port).apply(block).build()
