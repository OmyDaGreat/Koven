package xyz.malefic.koven.feature.auth.server

import arrow.core.raise.either
import org.http4k.client.JettyClient
import org.http4k.core.Credentials
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.removeCookie
import org.http4k.core.query
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.AccessToken
import org.http4k.security.CrossSiteRequestForgeryToken
import org.http4k.security.Nonce
import org.http4k.security.OAuthCallbackError
import org.http4k.security.OAuthPersistence
import org.http4k.security.OAuthProvider
import org.http4k.security.OAuthProviderConfig
import org.http4k.security.PkceChallengeAndVerifier
import org.http4k.security.openid.IdToken
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.server.cookie
import xyz.malefic.koven.server.register

/**
 * Server-side handler for [AuthType.OAuth], implementing [AuthHandler].
 */
object OAuthHandler : AuthHandler<AuthType.OAuth> {
    val serverClient = JettyClient()
    private const val OAUTH_FINALIZE_PATH = "auth/oauth/finalize"

    context(auth: AuthType.OAuth)
    override fun authRoutes(): RoutingHttpHandler {
        val oauthProvider = auth.provider
        val authUri = Uri.of(oauthProvider.authEndpoint)
        val finalizePath = "/${KovenConfig.apiPrefix}/$OAUTH_FINALIZE_PATH"

        val providerConfig =
            OAuthProviderConfig(
                authBase = authUri.copy(path = ""),
                authPath = authUri.path,
                tokenPath = oauthProvider.tokenEndpoint,
                credentials = Credentials(auth.clientId, auth.clientSecret),
            )

        val persistence = KovenOAuthPersistence(auth.useSecureCookies)

        val oauth =
            OAuthProvider(
                providerConfig = providerConfig,
                client = serverClient,
                callbackUri = Uri.of(auth.redirectUri),
                scopes = auth.scopes,
                oAuthPersistence = persistence,
            ) { req -> Uri.of(req.query("next") ?: finalizePath) }

        return routes( // TODO: Convert all manual routes to contracts
            "/${KovenConfig.apiPrefix}/auth/login/${oauthProvider.name.lowercase()}" bind Method.GET to
                oauth.authFilter.then { Response(Status.OK) },
            oauth.callbackEndpoint,
            RefreshContract.register { _ ->
                with(AuthService) { refresh() }
            },
            LogoutContract.register { _ ->
                with(AuthService) { logout() }
            },
            finalizePath bind Method.GET to req@{ request ->
                // TODO: Provide way more error info
                val token = persistence.retrieveToken(request) ?: return@req Response(Status.UNAUTHORIZED)
                val userInfoJson = fetchUserInfoJson(oauthProvider.userInfoEndpoint, token) ?: return@req Response(Status.UNAUTHORIZED)
                val (providerUserId, providerUsername) = oauthProvider.parseUserInfo(userInfoJson)

                val preferredUsername = request.query("username") ?: providerUsername

                val tokens =
                    transaction {
                        val user =
                            with(AuthService) {
                                findOAuthUser(oauthProvider.name, providerUserId)
                                    ?: either {
                                        linkOrCreateUser(
                                            oauthProvider.name,
                                            providerUserId,
                                            preferredUsername,
                                        )
                                    }.getOrNull()
                            } ?: return@transaction null

                        with(AuthService) {
                            with(auth as AuthType) {
                                user.issueTokenPair()
                            }
                        }
                    } ?: return@req Response(Status.INTERNAL_SERVER_ERROR)

                val redirectUri =
                    (persistence.retrieveOriginalUri(request) ?: Uri.of(auth.clientCallbackPath))
                        .query("auth_success", "true")

                Response(Status.FOUND) // TODO: Custom status codes w/ register
                    .header("Location", redirectUri.toString())
                    .cookie(AuthService.RefreshTokenCookie create tokens.refreshToken)
                    .removeCookie("koven_oauth_token")
            },
        )
    }

    private fun fetchUserInfoJson(
        endpoint: String?,
        token: AccessToken,
    ): String? {
        if (endpoint == null) return null
        val response = serverClient(Request(Method.GET, endpoint).header("Authorization", "Bearer ${token.value}"))
        return if (response.status == Status.OK) response.bodyString() else null
    }

    private class KovenOAuthPersistence(
        private val useSecure: Boolean,
    ) : OAuthPersistence {
        override fun assignCsrf(
            redirect: Response,
            csrf: CrossSiteRequestForgeryToken,
        ): Response = redirect.cookie(Cookie("koven_oauth_csrf", csrf.value, path = "/", httpOnly = true, secure = useSecure))

        override fun retrieveCsrf(request: Request): CrossSiteRequestForgeryToken? =
            request.cookie("koven_oauth_csrf")?.value?.let { CrossSiteRequestForgeryToken(it) }

        override fun assignNonce(
            redirect: Response,
            nonce: Nonce,
        ): Response = redirect.cookie(Cookie("koven_oauth_nonce", nonce.value, path = "/", httpOnly = true, secure = useSecure))

        override fun retrieveNonce(request: Request): Nonce? = request.cookie("koven_oauth_nonce")?.value?.let { Nonce(it) }

        override fun assignOriginalUri(
            redirect: Response,
            originalUri: Uri,
        ): Response = redirect.cookie(Cookie("koven_oauth_orig", originalUri.toString(), path = "/", httpOnly = true, secure = useSecure))

        override fun retrieveOriginalUri(request: Request): Uri? = request.cookie("koven_oauth_orig")?.value?.let { Uri.of(it) }

        override fun assignPkce(
            redirect: Response,
            pkce: PkceChallengeAndVerifier,
        ): Response =
            redirect
                .cookie(Cookie("koven_oauth_pkce_v", pkce.verifier, path = "/", httpOnly = true, secure = useSecure))
                .cookie(Cookie("koven_oauth_pkce_c", pkce.challenge, path = "/", httpOnly = true, secure = useSecure))

        override fun retrievePkce(request: Request): PkceChallengeAndVerifier? {
            val v = request.cookie("koven_oauth_pkce_v")?.value ?: return null
            val c = request.cookie("koven_oauth_pkce_c")?.value ?: return null
            return PkceChallengeAndVerifier(c, v)
        }

        override fun assignToken(
            request: Request,
            redirect: Response,
            accessToken: AccessToken,
            idToken: IdToken?,
        ): Response =
            redirect
                .cookie(Cookie("koven_oauth_token", accessToken.value, path = "/", httpOnly = true, secure = useSecure))
                .removeCookie("koven_oauth_csrf")
                .removeCookie("koven_oauth_nonce")
                .removeCookie("koven_oauth_orig")
                .removeCookie("koven_oauth_pkce_v")
                .removeCookie("koven_oauth_pkce_c")

        override fun retrieveToken(request: Request): AccessToken? = request.cookie("koven_oauth_token")?.value?.let { AccessToken(it) }

        override fun authFailureResponse(reason: OAuthCallbackError): Response =
            Response(Status.UNAUTHORIZED)
                .body(reason.toString())
                .removeCookie("koven_oauth_csrf")
                .removeCookie("koven_oauth_nonce")
                .removeCookie("koven_oauth_orig")
                .removeCookie("koven_oauth_pkce_v")
                .removeCookie("koven_oauth_pkce_c")
    }
}
