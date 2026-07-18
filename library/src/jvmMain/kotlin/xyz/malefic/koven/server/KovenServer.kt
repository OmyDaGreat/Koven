package xyz.malefic.koven.server

import arrow.core.raise.Raise
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.filter.debug
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.JettyLoom
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.ApiContract
import xyz.malefic.koven.core.HeaderProvider
import xyz.malefic.koven.core.PathProvider
import xyz.malefic.koven.core.QueryProvider
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.server.OAuthHandler
import xyz.malefic.koven.feature.auth.server.PasswordAuthHandler
import xyz.malefic.koven.feature.multipart.Multipart
import xyz.malefic.koven.feature.pagination.PaginatedResponse
import xyz.malefic.koven.feature.pagination.Pagination
import java.io.File

/**
 * A builder context for configuring routes and server settings.
 */
class KovenServerBuilder(
    val config: KovenServerConfig,
) {
    private val routes = mutableListOf<RoutingHttpHandler>()

    fun add(route: RoutingHttpHandler) {
        routes += route
    }

    /**
     * Adds an [ApiContract] to the server by registering a route with the given [handler].
     *
     * @param handler The function to handle the request.
     */
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>.handle(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Principal) Request.(Req) -> Any?,
    ) = add(register(handler))

    /**
     * Adds an [ApiContract] with a multipart request body to the server.
     *
     * @param handler The function to handle the request.
     */
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Multipart, Res, ReqH, ResH, PathP, QueryP>.handleMultipart(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Principal) Request.(Multipart) -> Any?,
    ) = add(registerMultipart(handler))

    /**
     * Adds a paginated [ApiContract] to the server.
     *
     * @param handler The function to handle the request.
     */
    @Suppress("ktlint:standard:max-line-length")
    inline fun <reified Req, reified T, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> ApiContract<Req, PaginatedResponse<T>, ReqH, ResH, PathP, QueryP>.handlePaginated(
        crossinline handler: context(Raise<Issue>, ReqH, PathP, QueryP, Pagination, Principal) Request.(Req) -> Any?,
    ) = add(registerPaginated(handler))

    internal fun buildHandler(): RoutingHttpHandler {
        val authHandlerRoutes =
            when (val auth = KovenConfig.auth) {
                is AuthType.NoAuth -> null
                is AuthType.Password -> with(PasswordAuthHandler) { with(auth) { authRoutes() } }
                is AuthType.OAuth -> with(OAuthHandler) { with(auth) { authRoutes() } }
            }

        return ServerFilters
            .Cors(config.corsPolicy)
            .then(
                routes(
                    listOfNotNull(
                        authHandlerRoutes,
                    ) +
                        routes +
                        if (config.assetsHosting) {
                            listOf("/${KovenConfig.assetsPrefix}" bind static(ResourceLoader.Directory(config.assetsPath)))
                        } else {
                            emptyList()
                        } +
                        if (config.filesHosting) {
                            listOf("/${KovenConfig.filesPrefix}" bind static(ResourceLoader.Directory(config.filesPath)))
                        } else {
                            emptyList()
                        },
                ),
            )
    }
}

/**
 * Singleton Koven server instance.
 *
 * Usage example:
 * ```
 * KovenServer.start {
 *     config.port = 8081
 *     PingContract.handle { "Pong" }
 * }.block()
 * ```
 */
object KovenServer {
    val config = KovenServerConfig()
    private var underlying: Http4kServer? = null

    val port get() = underlying?.port() ?: config.port

    /**
     * Configures and starts the server.
     *
     * @param httpConfig A function that configures the routes through [RoutingHttpHandler], akin to a [org.http4k.core.Filter].
     * @param serverConfig A function that configures the server through [KovenServerBuilder].
     *
     * @throws IllegalStateException If the server is already running.
     */
    fun start(
        server: (Int) -> ServerConfig = { JettyLoom(it) },
        httpConfig: RoutingHttpHandler.() -> RoutingHttpHandler = { debug() },
        serverConfig: KovenServerBuilder.() -> Unit = {},
    ): KovenServer =
        apply {
            if (underlying == null) {
                val builder = KovenServerBuilder(config).apply(serverConfig)
                val handler = builder.buildHandler().httpConfig()
                underlying = handler.asServer(server(config.port)).start()
            } else {
                error("Server is already running")
            }
        }

    /**
     * Stops the server.
     */
    fun stop(): KovenServer =
        apply {
            underlying?.stop()
            underlying = null
        }

    /**
     * Blocks the current thread until the server is stopped.
     */
    fun block(): KovenServer =
        apply {
            underlying?.block()
        }

    /**
     * Saves an asset to the [KovenServerConfig.assetsPath] directory.
     */
    fun saveAsset(
        name: String,
        bytes: ByteArray,
    ) {
        val dir = File(config.assetsPath)
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeBytes(bytes)
    }

    /**
     * Saves a file to the [KovenServerConfig.filesPath] directory.
     */
    fun saveFile(
        name: String,
        bytes: ByteArray,
    ) {
        val dir = File(config.filesPath)
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeBytes(bytes)
    }
}
