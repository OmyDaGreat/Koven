package xyz.malefic.koven.client.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import com.varabyte.kobweb.core.PageContext
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.url.URLSearchParams
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.client.call
import xyz.malefic.koven.core.NoHeader
import xyz.malefic.koven.core.NoParams
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.PasswordLoginContract
import xyz.malefic.koven.feature.auth.PasswordRegisterContract
import xyz.malefic.koven.feature.auth.PasswordStrengthContract
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.feature.auth.SimplePrincipal
import xyz.malefic.koven.feature.auth.model.UserRequestModel
import kotlin.uuid.Uuid

/**
 * Manages the client-side authentication session.
 */
object AuthSession {
    private const val ACCESS_TOKEN_KEY = "koven_access_token"
    private const val USER_ID_KEY = "koven_user_id"
    private const val USERNAME_KEY = "koven_username"

    /**
     * The current access token, or null if not authenticated.
     */
    var accessToken: String? by mutableStateOf(localStorage.getItem(ACCESS_TOKEN_KEY))
        private set

    /**
     * The current principal, or null if not authenticated.
     */
    var principal: Principal? by mutableStateOf(
        run {
            val id = localStorage.getItem(USER_ID_KEY)?.let { Uuid.parseOrNull(it) }
            val username = localStorage.getItem(USERNAME_KEY)
            if (id != null && username != null) SimplePrincipal(id, username) else null
        },
    )
        private set

    init {
        val params = URLSearchParams(window.location.search)
        if (params.get("auth_success") == "true") {
            window.history.replaceState(null, "", window.location.pathname)
            CoroutineScope(Dispatchers.Main).launch {
                refresh().onLeft {
                    // Silently fail if bootstrap refresh fails, user remains unauthenticated
                }
            }
        }
    }

    /**
     * Whether the user is currently authenticated.
     */
    val isAuthenticated: Boolean get() = accessToken != null

    /**
     * Performs a login request.
     *
     * If the current [KovenConfig.auth] is [AuthType.Password], [credentials] must be provided.
     * If it is [AuthType.OAuth], the browser will be redirected to the provider's login page.
     *
     * @param credentials Credentials for password login.
     * @param provider The OAuth provider name (if multiple are configured).
     */
    context(ctx: PageContext)
    suspend fun login(
        credentials: UserRequestModel? = null,
        provider: String? = null,
    ): Either<Issue, Unit> =
        when (val auth = KovenConfig.auth) {
            is AuthType.Password -> {
                if (credentials == null) return Either.Left(AuthIssue.InvalidCredentials("Credentials required for password login"))
                PasswordLoginContract.call(credentials, NoHeader, NoParams, NoParams).map {
                    updateSession(it.body.accessToken, it.body)
                }
            }

            is AuthType.OAuth -> {
                val selectedProvider =
                    provider ?: auth.providers.keys.firstOrNull()
                        ?: return Either.Left(AuthIssue.Unauthorized("No OAuth providers configured"))
                val next = window.location.href
                ctx.router.navigateTo("/${KovenConfig.apiPrefix}/auth/login/${selectedProvider.lowercase()}?next=$next")
                Either.Right(Unit)
            }

            else -> {
                Either.Left(AuthIssue.Unauthorized("Authentication is disabled"))
            }
        }

    /**
     * Initiates the linking of an OAuth provider to the current account.
     */
    context(ctx: PageContext)
    fun link(provider: String): Either<Issue, Unit> =
        when (val auth = KovenConfig.auth) {
            is AuthType.OAuth -> {
                if (!auth.providers.containsKey(provider)) return Either.Left(BadRequestIssue("Unknown provider: $provider"))
                val next = window.location.href
                ctx.router.navigateTo("/${KovenConfig.apiPrefix}/auth/link/${provider.lowercase()}?next=$next")
                Either.Right(Unit)
            }

            else -> {
                Either.Left(AuthIssue.Unauthorized("OAuth linking is not supported for the current auth type"))
            }
        }

    /**
     * Performs a registration request, specifically in [AuthType.Password].
     */
    suspend fun register(credentials: UserRequestModel): Either<Issue, Unit> =
        PasswordRegisterContract.call(credentials, NoHeader, NoParams, NoParams).map {
            updateSession(it.body.accessToken, it.body)
        }

    /**
     * Performs a token refresh request, specifically in [AuthType.Password].
     */
    suspend fun refresh(): Either<Issue, Unit> =
        RefreshContract.call(Unit, NoHeader, NoParams, NoParams).map {
            updateSession(it.body.accessToken, it.body)
        }

    /**
     * Performs a logout request.
     */
    suspend fun logout(): Either<Issue, Unit> =
        when (KovenConfig.auth) {
            is AuthType.NoAuth -> {
                clearSession()
                Either.Right(Unit)
            }

            else -> {
                LogoutContract.call(Unit, NoHeader, NoParams, NoParams).map {
                    clearSession()
                }
            }
        }

    /**
     * Estimates the strength of a password, specifically in [AuthType.Password].
     */
    suspend fun strength(password: String): Either<Issue, Pair<Int, String?>> =
        PasswordStrengthContract.call(password, NoHeader, NoParams, NoParams).map { it.body }

    /**
     * Updates the session with new authentication data.
     */
    private fun updateSession(
        token: String,
        user: Principal,
    ) {
        accessToken = token
        principal = user
        localStorage.setItem(ACCESS_TOKEN_KEY, token)
        localStorage.setItem(USER_ID_KEY, user.userId.toString())
        localStorage.setItem(USERNAME_KEY, user.username)
    }

    /**
     * Clears the current session.
     */
    private fun clearSession() {
        accessToken = null
        principal = null
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(USER_ID_KEY)
        localStorage.removeItem(USERNAME_KEY)
    }
}
