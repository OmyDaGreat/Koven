package xyz.malefic.koven.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.right
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
import xyz.malefic.koven.api.ApiResponse.Companion.withHeaders
import xyz.malefic.koven.core.field.Cookie
import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.core.field.Redirect
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

    private class ScopedCookies(
        val provider: String,
    ) {
        val csrf = oauthCookie(provider, "csrf")
        val nonce = oauthCookie(provider, "nonce")
        val origUri = oauthCookie(provider, "orig")
        val pkceVerifier = oauthCookie(provider, "pkce_v")
        val pkceChallenge = oauthCookie(provider, "pkce_c")
        val token = oauthCookie(provider, "token")
        val link = oauthCookie(provider, "link")

        private fun oauthCookie(
            provider: String,
            name: String,
        ) = object : CookieField<String> {
            override val name = "koven_oauth_${provider}_$name"

            override fun secure() = KovenConfig.auth.useSecureCookies

            override fun isHttpOnly() = true

            override fun path() = "/"

            context(_: Raise<Issue>)
            override fun decode(cookies: Map<String, String>) = cookies[this.name] ?: ""

            override fun encodeCookies(value: String): List<Cookie> = listOf(create(value))
        }
    }

    context(auth: AuthType.OAuth)
    override fun authRoutes(): RoutingHttpHandler {
        val providers = auth.providers
        val finalizePath = "/${KovenConfig.apiPrefix}/$OAUTH_FINALIZE_PATH"

        val oauthFilters =
            providers.mapValues { (name, config) ->
                val oauthProvider = config.provider
                val authUri = Uri.of(oauthProvider.authEndpoint)

                OAuthProvider(
                    providerConfig =
                        OAuthProviderConfig(
                            authBase = authUri.copy(path = ""),
                            authPath = authUri.path,
                            tokenPath = oauthProvider.tokenEndpoint,
                            credentials = Credentials(config.clientId, config.clientSecret),
                        ),
                    client = serverClient,
                    callbackUri = Uri.of(config.redirectUri),
                    scopes = config.scopes,
                    oAuthPersistence = KovenOAuthPersistence(auth.useSecureCookies, auth.clientCallbackPath, name),
                ) { req -> Uri.of(req.query("next") ?: finalizePath).query("provider", name) }
            }

        return routes(
            OAuthLoginContract.register { _, path, _ ->
                val providerName = path.provider
                val oauth = oauthFilters[providerName] ?: raise(BadRequestIssue("Unknown provider: $providerName"))
                oauth.authFilter { Response(Status.OK) }(this)
            },
            OAuthLinkContract.register { _, path, _ ->
                val providerName = path.provider
                val oauth = oauthFilters[providerName] ?: raise(BadRequestIssue("Unknown provider: $providerName"))
                oauth.authFilter { Response(Status.OK) }(this).cookie(ScopedCookies(providerName).link create "true")
            },
            routes(oauthFilters.values.map { it.callbackEndpoint }),
            RefreshContract.register {
                refresh()
            },
            LogoutContract.register {
                logout()
            },
            OAuthFinalizeContract.register { _, _, query ->
                val baseRedirect =
                    KovenOAuthPersistence(auth.useSecureCookies, auth.clientCallbackPath, query.provider)
                        .retrieveOriginalUri(this) ?: Uri.of(auth.clientCallbackPath)

                fun errorRedirect(issue: Issue): ApiResponse<Unit, Headers> =
                    302 withHeaders
                        Redirect.createHeaders(
                            baseRedirect
                                .query("auth_success", "false")
                                .query("error", issue.javaClass.simpleName)
                                .query("message", issue.message)
                                .toString(),
                        )

                val result =
                    either {
                        val providerName = ensureNotNull(query.provider) { BadRequestIssue("Provider query param missing") }
                        val cookies = ScopedCookies(providerName)
                        val isLinking = this@register[cookies.link] == "true"
                        val oauthProvider =
                            ensureNotNull(auth.providers[providerName]?.provider) { BadRequestIssue("Unknown provider: $providerName") }

                        val token =
                            ensureNotNull(
                                KovenOAuthPersistence(auth.useSecureCookies, auth.clientCallbackPath, providerName)
                                    .retrieveToken(this@register),
                            ) { raise(AuthIssue.OAuthIssue.TokenExchangeFailed()) }
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

                        Pair(tokens, cookies)
                    }

                val (tokens, cookies) = result.getOrElse { return@register errorRedirect(it) }
                val redirectUri = baseRedirect.query("auth_success", "true")

                302
                    .withHeaders(Redirect.createHeaders(redirectUri.toString()))
                    .with(AuthService.RefreshTokenCookie create tokens.refreshToken)
                    .with(cookies.token.clear())
                    .with(cookies.link.clear())
            },
        )
    }

    private fun fetchUserInfoJson(
        endpoint: String?,
        token: AccessToken,
    ): Either<Issue, String> {
        if (endpoint == null) return AuthIssue.OAuthIssue.UserInfoFetchFailed("No UserInfo endpoint configured").left()
        val response = serverClient(Request(org.http4k.core.Method.GET, endpoint).header("Authorization", "Bearer ${token.value}"))
        return if (response.status == Status.OK) {
            response.bodyString().right()
        } else {
            AuthIssue.OAuthIssue.UserInfoFetchFailed("Provider returned ${response.status}").left()
        }
    }

    private class KovenOAuthPersistence(
        private val useSecure: Boolean,
        private val clientCallbackPath: String,
        private val providerName: String? = null,
    ) : OAuthPersistence {
        private val cookies = providerName?.let { ScopedCookies(it) }

        override fun assignCsrf(
            redirect: Response,
            csrf: CrossSiteRequestForgeryToken,
        ): Response = if (cookies != null) redirect.cookie(cookies.csrf create csrf.value) else redirect

        override fun retrieveCsrf(request: Request): CrossSiteRequestForgeryToken? =
            cookies?.let { either { request[it.csrf] }.getOrNull() }?.let { CrossSiteRequestForgeryToken(it) }

        override fun assignNonce(
            redirect: Response,
            nonce: Nonce,
        ): Response = if (cookies != null) redirect.cookie(cookies.nonce create nonce.value) else redirect

        override fun retrieveNonce(request: Request): Nonce? = cookies?.let { either { request[it.nonce] }.getOrNull() }?.let { Nonce(it) }

        override fun assignOriginalUri(
            redirect: Response,
            originalUri: Uri,
        ): Response = if (cookies != null) redirect.cookie(cookies.origUri create originalUri.toString()) else redirect

        override fun retrieveOriginalUri(request: Request): Uri? =
            cookies?.let { either { request[it.origUri] }.getOrNull() }?.let { Uri.of(it) }

        override fun assignPkce(
            redirect: Response,
            pkce: PkceChallengeAndVerifier,
        ): Response =
            if (cookies != null) {
                redirect
                    .cookie(cookies.pkceVerifier create pkce.verifier)
                    .cookie(cookies.pkceChallenge create pkce.challenge)
            } else {
                redirect
            }

        override fun retrievePkce(request: Request): PkceChallengeAndVerifier? {
            val cks = cookies ?: return null
            val v = either { request[cks.pkceVerifier] }.getOrNull() ?: return null
            val c = either { request[cks.pkceChallenge] }.getOrNull() ?: return null
            return PkceChallengeAndVerifier(c, v)
        }

        override fun assignToken(
            request: Request,
            redirect: Response,
            accessToken: AccessToken,
            idToken: IdToken?,
        ): Response =
            if (cookies != null) {
                redirect
                    .cookie(cookies.token create accessToken.value)
                    .cookie(cookies.csrf.clear())
                    .cookie(cookies.nonce.clear())
                    .cookie(cookies.origUri.clear())
                    .cookie(cookies.pkceVerifier.clear())
                    .cookie(cookies.pkceChallenge.clear())
            } else {
                redirect
            }

        override fun retrieveToken(request: Request): AccessToken? =
            cookies?.let { either { request[it.token] }.getOrNull() }?.let { AccessToken(it) }

        override fun authFailureResponse(reason: OAuthCallbackError): Response {
            val uri =
                Uri
                    .of(clientCallbackPath)
                    .query("auth_success", "false")
                    .query("error", "callback_failed")
                    .query("reason", reason.toString())
            var response =
                Response(Status.FOUND)
                    .header("Location", uri.toString())

            if (cookies != null) {
                response =
                    response
                        .cookie(cookies.csrf.clear())
                        .cookie(cookies.nonce.clear())
                        .cookie(cookies.origUri.clear())
                        .cookie(cookies.pkceVerifier.clear())
                        .cookie(cookies.pkceChallenge.clear())
            }
            return response
        }
    }
}
