package xyz.malefic.koven.auth.server

import arrow.core.raise.Raise
import org.http4k.core.Request
import org.http4k.routing.RoutingHttpHandler
import xyz.malefic.koven.auth.AuthType
import xyz.malefic.koven.auth.Principal
import xyz.malefic.koven.error.Issue

/**
 * Interface for server-side authentication handlers.
 */
interface AuthHandler<T : AuthType> {
    /**
     * Standard authentication routes provided by the handler.
     */
    context(auth: T)
    fun authRoutes(): RoutingHttpHandler

    /**
     * Authenticates the given [request] and returns the [Principal] if successful.
     */
    context(auth: T, _: Raise<Issue>)
    fun authenticate(request: Request): Principal = AuthService.authenticate(request)
}
