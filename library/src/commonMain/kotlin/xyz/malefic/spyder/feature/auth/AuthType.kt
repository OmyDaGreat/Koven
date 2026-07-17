package xyz.malefic.spyder.feature.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * The configuration for authentication.
 */
sealed interface AuthType {
    /**
     * Completely opens all auth-handled endpoints.
     */
    data object NoAuth : AuthType

    /**
     * Managed by the framework, with automatic handling of token issuing, rotation, and cookies.
     */
    data class Password(
        val accessTokenTtl: Duration = 15.minutes,
        val refreshTokenTtl: Duration = 30.days,
        val cookieDomain: String? = null,
    ) : AuthType

    /**
     * Delegated to [OAuthProvider] with standard OAuth interceptors.
     */
    data class OAuth(
        val provider: OAuthProvider,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val scopes: List<String> = listOf("openid", "profile", "email"),
    ) : AuthType
}
