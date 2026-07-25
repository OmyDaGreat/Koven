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
    /**
     * Completely opens all auth-handled endpoints.
     */
    data object NoAuth : AuthType

    /**
     * Managed by the framework, with automatic handling of token issuing, rotation, and cookies.
     */
    data class Password(
        val validation: Validation<UserRequestModel> = defaultPasswordValidation,
        val accessTokenTtl: Duration = 15.minutes,
        val refreshTokenTtl: Duration = 30.days,
        val maxFailedAttempts: Int = 5,
        val lockOutDuration: Duration = 15.minutes,
        val cookieDomain: String? = null,
        val useSecureCookies: Boolean = true,
    ) : AuthType

    /**
     * Delegated to [OAuthProvider] with standard OAuth interceptors. Supports multiple providers.
     */
    data class OAuth(
        val providers: Map<String, ProviderConfig>,
        val clientCallbackPath: String,
        val accessTokenTtl: Duration = 15.minutes,
        val refreshTokenTtl: Duration = 30.days,
        val maxFailedAttempts: Int = 5,
        val lockOutDuration: Duration = 15.minutes,
        val cookieDomain: String? = null,
        val useSecureCookies: Boolean = true,
    ) : AuthType {
        /**
         * Configuration for an OAuth provider.
         */
        data class ProviderConfig(
            val provider: OAuthProvider,
            val clientId: String,
            val clientSecret: String,
            val redirectUri: String,
            val scopes: List<String> = provider.defaultScopes,
        )
    }

    // TODO: Support combining multiple auth types (user can choose) since AuthService already commonizes much of the code

    // TODO: Add Api Key authentication
}
