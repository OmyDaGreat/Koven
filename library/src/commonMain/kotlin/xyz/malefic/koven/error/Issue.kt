package xyz.malefic.koven.error

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable

/**
 * A base class for all API issues.
 * Shared between client and server for type-safe error handling.
 *
 * @property message The error message.
 * @property status The HTTP status code. Should be something supported by http4k.
 *
 * @see <a href=https://github.com/http4k/http4k/blob/master/core/core/src/main/kotlin/org/http4k/core/Status.kt#L5>http4k Status</a>
 */
@Serializable
abstract class Issue {
    abstract val message: String
    abstract val status: Int
}

/**
 * Common issue for internal server errors.
 */
@Serializable
data class InternalIssue(
    override val message: String = "Internal Server Error",
    override val status: Int = 500,
) : Issue() {
    companion object {
        infix fun from(e: Throwable): InternalIssue {
            Logger.e(e) { "Internal Server Error: ${e.message}" }
            return InternalIssue(e.message ?: "Internal Server Error")
        }
    }
}

/**
 * Common issue for validation or bad request errors.
 */
@Serializable
data class BadRequestIssue(
    override val message: String = "Bad Request",
    override val status: Int = 400,
) : Issue()

/**
 * Common issue for authentication errors.
 */
@Serializable
sealed class AuthIssue : Issue() {
    @Serializable
    data class Unauthorized(
        override val message: String = "Unauthorized",
        override val status: Int = 401,
    ) : AuthIssue()

    @Serializable
    data class InvalidCredentials(
        override val message: String = "Invalid username or password",
        override val status: Int = 401,
    ) : AuthIssue()

    @Serializable
    data class InvalidToken(
        override val message: String = "Invalid or expired token",
        override val status: Int = 401,
    ) : AuthIssue()

    @Serializable
    data class AccountLocked(
        val unlockAt: Long,
        override val status: Int = 401,
    ) : AuthIssue() {
        override val message = "Account locked until $unlockAt"
    }

    @Serializable
    data class MissingToken(
        override val message: String = "Missing authentication token",
        override val status: Int = 401,
    ) : AuthIssue()

    /**
     * Common issues for OAuth flow.
     */
    @Serializable
    sealed class OAuthIssue : AuthIssue() {
        @Serializable
        data class TokenExchangeFailed(
            override val message: String = "Failed to exchange OAuth code for access token",
            override val status: Int = 401,
        ) : OAuthIssue()

        @Serializable
        data class UserInfoFetchFailed(
            override val message: String = "Failed to fetch user information from OAuth provider",
            override val status: Int = 401,
        ) : OAuthIssue()

        @Serializable
        data class ProviderUserMappingFailed(
            override val message: String = "Failed to map provider user data to a local user",
            override val status: Int = 401,
        ) : OAuthIssue()
    }
}

/**
 * Common issue for user-related errors.
 */
@Serializable
sealed class UserIssue : Issue() {
    @Serializable
    data class AlreadyExists(
        override val message: String = "User already exists",
        override val status: Int = 400,
    ) : UserIssue()

    @Serializable
    data class NotFound(
        override val message: String = "User not found",
        override val status: Int = 404,
    ) : UserIssue()

    @Serializable
    data class InvalidUser(
        val usernameIssues: List<String> = emptyList(),
        val passwordIssues: List<String> = emptyList(),
        override val status: Int = 400,
    ) : UserIssue() {
        override val message: String =
            buildList {
                if (usernameIssues.isNotEmpty()) add("Invalid username: ${usernameIssues.joinToString(", ")}")
                if (passwordIssues.isNotEmpty()) add("Invalid password: ${passwordIssues.joinToString(", ")}")
            }.joinToString("; ")
    }
}

/**
 * Common issue for rate limiting.
 */
@Serializable
data class RateLimitedIssue(
    override val message: String = "Too many requests",
    override val status: Int = 429,
    val retryAfterMs: Long? = null,
) : Issue()
