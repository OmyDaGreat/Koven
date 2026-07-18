package xyz.malefic.koven.client.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import kotlinx.browser.localStorage
import xyz.malefic.koven.client.call
import xyz.malefic.koven.core.NoHeader
import xyz.malefic.koven.core.NoParams
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.LoginContract
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.PasswordStrengthContract
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.feature.auth.RegisterContract
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

    /**
     * Whether the user is currently authenticated.
     */
    val isAuthenticated: Boolean get() = accessToken != null

    /**
     * Performs a login request.
     */
    suspend fun login(credentials: UserRequestModel): Either<Issue, Unit> =
        LoginContract.call(credentials, NoHeader, NoParams, NoParams).map {
            updateSession(it.body.accessToken, it.body)
        }

    /**
     * Performs a registration request.
     */
    suspend fun register(credentials: UserRequestModel): Either<Issue, Unit> =
        RegisterContract.call(credentials, NoHeader, NoParams, NoParams).map {
            updateSession(it.body.accessToken, it.body)
        }

    /**
     * Performs a token refresh request.
     */
    suspend fun refresh(): Either<Issue, Unit> =
        RefreshContract.call(Unit, NoHeader, NoParams, NoParams).map {
            updateSession(it.body.accessToken, it.body)
        }

    /**
     * Performs a logout request.
     */
    suspend fun logout(): Either<Issue, Unit> =
        LogoutContract.call(Unit, NoHeader, NoParams, NoParams).map {
            clearSession()
        }

    /**
     * Estimates the strength of a password.
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
