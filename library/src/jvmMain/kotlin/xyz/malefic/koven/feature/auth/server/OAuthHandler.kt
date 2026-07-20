package xyz.malefic.koven.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
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
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.OAuthFinalizeContract
import xyz.malefic.koven.feature.auth.OAuthFinalizeQuery
import xyz.malefic.koven.feature.auth.OAuthLoginContract
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.server.cookie
import xyz.malefic.koven.server.get
import xyz.malefic.koven.server.register

/**
 * Server-side handler for [AuthType.OAuth], implementing [AuthHandler].
 */
object OAuthHandler : AuthHandler<AuthType.OAuth> {
    val serverClient = JettyClient()
    private const val OAUTH_FINALIZE_PATH = "auth/oauth/finalize"

    private val CsrfCookie = oauthCookie("csrf")
    private val NonceCookie = oauthCookie("nonce")
    private val OrigUriCookie = oauthCookie("orig")
    private val PkceVerifierCookie = oauthCookie("pkce_v")
    private val PkceChallengeCookie = oauthCookie("pkce_c")
    private val OAuthTokenCookie = oauthCookie("token")

    private fun oauthCookie(name: String) =
        object : CookieField<String> {
            override val name = "koven_oauth_$name"

            override fun secure() = KovenConfig.auth.useSecureCookies

            override fun isHttpOnly() = true

            override fun path() = "/"

            context(_: Raise<Issue>)
            override fun decode(cookies: Map<String, String>) = cookies[this.name] ?: ""
        }

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

        val persistence = KovenOAuthPersistence(auth.useSecureCookies, auth.clientCallbackPath)

        val oauth =
            OAuthProvider(
                providerConfig = providerConfig,
                client = serverClient,
                callbackUri = Uri.of(auth.redirectUri),
                scopes = auth.scopes,
                oAuthPersistence = persistence,
            ) { req -> Uri.of(req.query("next") ?: finalizePath) }

        return routes(
            OAuthLoginContract.register(oauth.authFilter) {},
            oauth.callbackEndpoint,
            RefreshContract.register { _ ->
                with(AuthService) { refresh() }
            },
            LogoutContract.register { _ ->
                with(AuthService) { logout() }
            },
            OAuthFinalizeContract.register { _ ->
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

                val token = persistence.retrieveToken(this) ?: return@register errorRedirect(AuthIssue.OAuthIssue.TokenExchangeFailed())

                val userInfoJson = fetchUserInfoJson(oauthProvider.userInfoEndpoint, token).getOrElse { return@register errorRedirect(it) }

                val (providerUserId, providerUsername) = oauthProvider.parseUserInfo(userInfoJson)
                val preferredUsername = contextOf<OAuthFinalizeQuery>().username ?: providerUsername

                val tokensResult =
                    transaction {
                        either {
                            val user =
                                with(AuthService) {
                                    findOAuthUser(oauthProvider.name, providerUserId)
                                        ?: linkOrCreateUser(
                                            oauthProvider.name,
                                            providerUserId,
                                            preferredUsername,
                                        )
                                }

                            with(AuthService) {
                                with(auth as AuthType) {
                                    user.issueTokenPair()
                                }
                            }
                        }
                    }

                val tokens = tokensResult.getOrElse { return@register errorRedirect(it) }

                val redirectUri = baseRedirect.query("auth_success", "true")

                302
                    .with(Redirect(redirectUri.toString()))
                    .with(AuthService.RefreshTokenCookie create tokens.refreshToken)
                    .with(OAuthTokenCookie.clear())
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
        ): Response = redirect.cookie(CsrfCookie create csrf.value)

        override fun retrieveCsrf(request: Request): CrossSiteRequestForgeryToken? =
            either { request[CsrfCookie] }.getOrNull()?.let { CrossSiteRequestForgeryToken(it) }

        override fun assignNonce(
            redirect: Response,
            nonce: Nonce,
        ): Response = redirect.cookie(NonceCookie create nonce.value)

        override fun retrieveNonce(request: Request): Nonce? = either { request[NonceCookie] }.getOrNull()?.let { Nonce(it) }

        override fun assignOriginalUri(
            redirect: Response,
            originalUri: Uri,
        ): Response = redirect.cookie(OrigUriCookie create originalUri.toString())

        override fun retrieveOriginalUri(request: Request): Uri? = either { request[OrigUriCookie] }.getOrNull()?.let { Uri.of(it) }

        override fun assignPkce(
            redirect: Response,
            pkce: PkceChallengeAndVerifier,
        ): Response =
            redirect
                .cookie(PkceVerifierCookie create pkce.verifier)
                .cookie(PkceChallengeCookie create pkce.challenge)

        override fun retrievePkce(request: Request): PkceChallengeAndVerifier? {
            val v = either { request[PkceVerifierCookie] }.getOrNull() ?: return null
            val c = either { request[PkceChallengeCookie] }.getOrNull() ?: return null
            return PkceChallengeAndVerifier(c, v)
        }

        override fun assignToken(
            request: Request,
            redirect: Response,
            accessToken: AccessToken,
            idToken: IdToken?,
        ): Response =
            redirect
                .cookie(OAuthTokenCookie create accessToken.value)
                .cookie(CsrfCookie.clear())
                .cookie(NonceCookie.clear())
                .cookie(OrigUriCookie.clear())
                .cookie(PkceVerifierCookie.clear())
                .cookie(PkceChallengeCookie.clear())

        override fun retrieveToken(request: Request): AccessToken? =
            either { request[OAuthTokenCookie] }.getOrNull()?.let { AccessToken(it) }

        override fun authFailureResponse(reason: OAuthCallbackError): Response {
            val uri =
                Uri
                    .of(clientCallbackPath)
                    .query("auth_success", "false")
                    .query("error", "callback_failed")
                    .query("reason", reason.toString())
            return Response(Status.FOUND)
                .header("Location", uri.toString())
                .cookie(CsrfCookie.clear())
                .cookie(NonceCookie.clear())
                .cookie(OrigUriCookie.clear())
                .cookie(PkceVerifierCookie.clear())
                .cookie(PkceChallengeCookie.clear())
        }
    }
}
