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
     * Locally managed, with the framework automatically handling token issuing, rotation, and cookies.
     */
    data class Local(
        val accessTokenTtl: Duration = 15.minutes,
        val refreshTokenTtl: Duration = 30.days,
        val cookieDomain: String? = null,
    ) : AuthType

    /**
     * Delegated to [OAuthProviderType] with standard OAuth interceptors.
     */
    data class OAuth2(
        val provider: OAuthProviderType,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val scopes: List<String> = listOf("openid", "profile", "email"),
    ) : AuthType

    /**
     * Passwordless authentication utilizing hardware biometrics/passkeys.
     */
    data class WebAuthn(
        val rpId: String,
        val rpName: String,
        val appOrigin: String,
        val requireUserVerification: Boolean = true,
    ) : AuthType
}
