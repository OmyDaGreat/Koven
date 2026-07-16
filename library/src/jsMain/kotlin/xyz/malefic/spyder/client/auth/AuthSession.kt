package xyz.malefic.spyder.client.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.localStorage
import xyz.malefic.spyder.feature.auth.Principal
import xyz.malefic.spyder.feature.auth.SimplePrincipal
import kotlin.uuid.Uuid

/**
 * Manages the client-side authentication session.
 */
object AuthSession {
    private const val ACCESS_TOKEN_KEY = "spyder_access_token"
    private const val USER_ID_KEY = "spyder_user_id"
    private const val USERNAME_KEY = "spyder_username"

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
     * Updates the session with new authentication data.
     */
    fun login(
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
    fun logout() {
        accessToken = null
        principal = null
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(USER_ID_KEY)
        localStorage.removeItem(USERNAME_KEY)
    }
}
