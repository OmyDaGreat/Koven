package xyz.malefic.koven.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import org.http4k.client.JettyClient
import org.http4k.core.Credentials
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.routing.RoutingHttpHandler
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
import xyz.malefic.koven.api.ApiResponse
import xyz.malefic.koven.api.ApiResponse.Companion.with
import xyz.malefic.koven.core.CookieField
import xyz.malefic.koven.core.Redirect
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.OAuthFinalizeContract
import xyz.malefic.koven.feature.auth.OAuthLinkContract
import xyz.malefic.koven.feature.auth.OAuthLoginContract
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.feature.auth.server.AuthService.issueTokenPair
import xyz.malefic.koven.feature.auth.server.AuthService.linkOrCreateUser
import xyz.malefic.koven.feature.auth.server.AuthService.logout
import xyz.malefic.koven.feature.auth.server.AuthService.refresh
import xyz.malefic.koven.server.cookie
import xyz.malefic.koven.server.get
import xyz.malefic.koven.server.register

/**
 * Server-side handler for [AuthType.OAuth], implementing [AuthHandler].
 */
object OAuthHandler : AuthHandler<AuthType.OAuth> {
    val serverClient = JettyClient()
    private const val OAUTH_FINALIZE_PATH = "auth/oauth/finalize"

    private object Cookies {
        val Csrf = oauthCookie("csrf")
        val Nonce = oauthCookie("nonce")
        val OrigUri = oauthCookie("orig")
        val PkceVerifier = oauthCookie("pkce_v")
        val PkceChallenge = oauthCookie("pkce_c")
        val Token = oauthCookie("token")
        val Provider = oauthCookie("provider")
        val Link = oauthCookie("link")

        private fun oauthCookie(name: String) =
            object : CookieField<String> {
                override val name = "koven_oauth_$name"

                override fun secure() = KovenConfig.auth.useSecureCookies

                override fun isHttpOnly() = true

                override fun path() = "/"

                context(_: Raise<Issue>)
                override fun decode(cookies: Map<String, String>) = cookies[this.name] ?: ""
            }
    }

    context(auth: AuthType.OAuth)
    override fun authRoutes(): RoutingHttpHandler {
        val providers = auth.providers
        val finalizePath = "/${KovenConfig.apiPrefix}/$OAUTH_FINALIZE_PATH"

        val persistence = KovenOAuthPersistence(auth.useSecureCookies, auth.clientCallbackPath)

        val oauthFilters =
            providers.mapValues { (_, config) ->
                val oauthProvider = config.provider
                val authUri = Uri.of(oauthProvider.authEndpoint)

                val providerConfig =
                    OAuthProviderConfig(
                        authBase = authUri.copy(path = ""),
                        authPath = authUri.path,
                        tokenPath = oauthProvider.tokenEndpoint,
                        credentials = Credentials(config.clientId, config.clientSecret),
                    )

                OAuthProvider(
                    providerConfig = providerConfig,
                    client = serverClient,
                    callbackUri = Uri.of(config.redirectUri),
                    scopes = config.scopes,
                    oAuthPersistence = persistence,
                ) { req -> Uri.of(req.query("next") ?: finalizePath) }
            }

        return routes(
            OAuthLoginContract.register { _, path, _ ->
                val providerName = path.provider
                val oauth = oauthFilters[providerName] ?: raise(BadRequestIssue("Unknown provider: $providerName"))
                oauth.authFilter { Response(Status.OK) }(this).cookie(Cookies.Provider create providerName)
            },
            OAuthLinkContract.register { _, path, _ ->
                val providerName = path.provider
                val oauth = oauthFilters[providerName] ?: raise(BadRequestIssue("Unknown provider: $providerName"))
                oauth
                    .authFilter { Response(Status.OK) }(this)
                    .cookie(Cookies.Provider create providerName)
                    .cookie(Cookies.Link create "true")
            },
            routes(oauthFilters.values.map { it.callbackEndpoint }),
            RefreshContract.register { _, _, _ ->
                refresh()
            },
            LogoutContract.register { _, _, _ ->
                logout()
            },
            OAuthFinalizeContract.register { _, _, query ->
                val baseRedirect = persistence.retrieveOriginalUri(this) ?: Uri.of(auth.clientCallbackPath)

                fun errorRedirect(issue: Issue): ApiResponse<Unit, Redirect> =
                    302 with
                        Redirect(
                            baseRedirect
                                .query("auth_success", "false")
                                .query("error", issue.javaClass.simpleName)
                                .query("message", issue.message)
                                .toString(),
                        )

                val result =
                    either {
                        val providerName = this@register[Cookies.Provider]
                        val isLinking = this@register[Cookies.Link] == "true"
                        val oauthProvider =
                            auth.providers[providerName]?.provider
                                ?: raise(BadRequestIssue("Unknown provider: $providerName"))

                        val token = persistence.retrieveToken(this@register) ?: raise(AuthIssue.OAuthIssue.TokenExchangeFailed())
                        val userInfoJson = fetchUserInfoJson(oauthProvider.userInfoEndpoint, token).bind()
                        val (providerUserId, providerUsername, email) = oauthProvider.parseUserInfo(userInfoJson)

                        val preferredUsername = query.username ?: providerUsername
                        val currentPrincipal = if (isLinking) either { authenticate(this@register) }.getOrNull() else null

                        val tokens =
                            transaction {
                                linkOrCreateUser(
                                    oauthProvider.name,
                                    providerUserId,
                                    preferredUsername,
                                    email,
                                    currentPrincipal = currentPrincipal,
                                ).issueTokenPair()
                            }

                        tokens
                    }

                val tokens = result.getOrElse { return@register errorRedirect(it) }
                val redirectUri = baseRedirect.query("auth_success", "true")

                302
                    .with(Redirect(redirectUri.toString()))
                    .with(AuthService.RefreshTokenCookie create tokens.refreshToken)
                    .with(Cookies.Token.clear())
                    .with(Cookies.Provider.clear())
                    .with(Cookies.Link.clear())
            },
        )
    }

    private fun fetchUserInfoJson(
        endpoint: String?,
        token: AccessToken,
    ): Either<Issue, String> {
        if (endpoint == null) return Either.Left(AuthIssue.OAuthIssue.UserInfoFetchFailed("No UserInfo endpoint configured"))
        val response = serverClient(Request(org.http4k.core.Method.GET, endpoint).header("Authorization", "Bearer ${token.value}"))
        return if (response.status == Status.OK) {
            Either.Right(response.bodyString())
        } else {
            Either.Left(AuthIssue.OAuthIssue.UserInfoFetchFailed("Provider returned ${response.status}"))
        }
    }

    private class KovenOAuthPersistence(
        private val useSecure: Boolean,
        private val clientCallbackPath: String,
    ) : OAuthPersistence {
        override fun assignCsrf(
            redirect: Response,
            csrf: CrossSiteRequestForgeryToken,
        ): Response = redirect.cookie(Cookies.Csrf create csrf.value)

        override fun retrieveCsrf(request: Request): CrossSiteRequestForgeryToken? =
            either { request[Cookies.Csrf] }.getOrNull()?.let { CrossSiteRequestForgeryToken(it) }

        override fun assignNonce(
            redirect: Response,
            nonce: Nonce,
        ): Response = redirect.cookie(Cookies.Nonce create nonce.value)

        override fun retrieveNonce(request: Request): Nonce? = either { request[Cookies.Nonce] }.getOrNull()?.let { Nonce(it) }

        override fun assignOriginalUri(
            redirect: Response,
            originalUri: Uri,
        ): Response = redirect.cookie(Cookies.OrigUri create originalUri.toString())

        override fun retrieveOriginalUri(request: Request): Uri? = either { request[Cookies.OrigUri] }.getOrNull()?.let { Uri.of(it) }

        override fun assignPkce(
            redirect: Response,
            pkce: PkceChallengeAndVerifier,
        ): Response =
            redirect
                .cookie(Cookies.PkceVerifier create pkce.verifier)
                .cookie(Cookies.PkceChallenge create pkce.challenge)

        override fun retrievePkce(request: Request): PkceChallengeAndVerifier? {
            val v = either { request[Cookies.PkceVerifier] }.getOrNull() ?: return null
            val c = either { request[Cookies.PkceChallenge] }.getOrNull() ?: return null
            return PkceChallengeAndVerifier(c, v)
        }

        override fun assignToken(
            request: Request,
            redirect: Response,
            accessToken: AccessToken,
            idToken: IdToken?,
        ): Response =
            redirect
                .cookie(Cookies.Token create accessToken.value)
                .cookie(Cookies.Csrf.clear())
                .cookie(Cookies.Nonce.clear())
                .cookie(Cookies.OrigUri.clear())
                .cookie(Cookies.PkceVerifier.clear())
                .cookie(Cookies.PkceChallenge.clear())

        override fun retrieveToken(request: Request): AccessToken? = either { request[Cookies.Token] }.getOrNull()?.let { AccessToken(it) }

        override fun authFailureResponse(reason: OAuthCallbackError): Response {
            val uri =
                Uri
                    .of(clientCallbackPath)
                    .query("auth_success", "false")
                    .query("error", "callback_failed")
                    .query("reason", reason.toString())
            return Response(Status.FOUND)
                .header("Location", uri.toString())
                .cookie(Cookies.Csrf.clear())
                .cookie(Cookies.Nonce.clear())
                .cookie(Cookies.OrigUri.clear())
                .cookie(Cookies.PkceVerifier.clear())
                .cookie(Cookies.PkceChallenge.clear())
        }
    }
}
