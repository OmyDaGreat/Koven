package xyz.malefic.koven.feature.auth

import io.konform.validation.Validation
import xyz.malefic.koven.feature.auth.model.UserRequestModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * The configuration for authentication.
 */
sealed interface AuthType {
    val accessTokenTtl: Duration
        get() = 15.minutes
    val refreshTokenTtl: Duration
        get() = 30.days
    val maxFailedAttempts: Int
        get() = 5
    val lockOutDuration: Duration
        get() = 15.minutes
    val cookieDomain: String?
        get() = null
    val useSecureCookies: Boolean
        get() = true

    /**
     * Completely opens all auth-handled endpoints.
     */
    data object NoAuth : AuthType

    /**
     * Managed by the framework, with automatic handling of token issuing, rotation, and cookies.
     */
    data class Password(
        override val cookieDomain: String? = null,
        override val useSecureCookies: Boolean = true,
        val validation: Validation<UserRequestModel> = defaultPasswordValidation,
    ) : AuthType

    /**
     * Delegated to [OAuthProvider] with standard OAuth interceptors.
     */
    data class OAuth(
        val provider: OAuthProvider, // TODO: Support multiple providers at once
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val clientCallbackPath: String,
        override val cookieDomain: String? = null,
        override val useSecureCookies: Boolean = true,
        val scopes: List<String> = listOf("openid", "profile", "email"),
    ) : AuthType

    // TODO: Support combining the two auth types (user can choose) since AuthService already commonizes much of the code
}
