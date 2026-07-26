package xyz.malefic.koven.auth

import io.konform.validation.Validation
import xyz.malefic.koven.auth.model.UserRequestModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * The configuration for authentication.
 */
sealed interface AuthType {
    val accessTokenTtl: Duration get() = 15.minutes
    val refreshTokenTtl: Duration get() = 30.days
    val cookieDomain: String? get() = null
    val useSecureCookies: Boolean get() = true

    /**
     * Completely opens all auth-handled endpoints.
     */
    data object NoAuth : AuthType

    /**
     * Managed by the framework, with automatic handling of token issuing, rotation, and cookies.
     */
    data class Password(
        val validation: Validation<UserRequestModel> = defaultPasswordValidation,
        override val accessTokenTtl: Duration = 15.minutes,
        override val refreshTokenTtl: Duration = 30.days,
        val maxFailedAttempts: Int = 5,
        val lockOutDuration: Duration = 15.minutes,
        override val cookieDomain: String? = null,
        override val useSecureCookies: Boolean = true,
    ) : AuthType

    /**
     * Delegated to [OAuthProvider] with standard OAuth interceptors. Supports multiple providers.
     */
    data class OAuth(
        val providers: Map<String, ProviderConfig>,
        val clientCallbackPath: String,
        override val accessTokenTtl: Duration = 15.minutes,
        override val refreshTokenTtl: Duration = 30.days,
        val maxFailedAttempts: Int = 5,
        val lockOutDuration: Duration = 15.minutes,
        override val cookieDomain: String? = null,
        override val useSecureCookies: Boolean = true,
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

    /**
     * API key based authentication, intended for machine-to-machine access. Keys are issued per-user and sent via the
     * [ApiKeyAuth] header. Managing keys (issuing/listing/revoking) is itself protected by this same auth type, so an
     * existing key (or a future combined auth type, see below) is required to mint additional keys.
     */
    data class ApiKey(
        val defaultTtl: Duration? = null,
        val keyPrefixLength: Int = 8,
    ) : AuthType

    // TODO: Support combining multiple auth types (user can choose) since AuthService already commonizes much of the code
}
