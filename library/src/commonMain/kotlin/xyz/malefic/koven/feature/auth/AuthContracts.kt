package xyz.malefic.koven.feature.auth

import arrow.core.raise.Raise
import xyz.malefic.koven.api.HttpMethod.GET
import xyz.malefic.koven.api.apiContract
import xyz.malefic.koven.core.PathField
import xyz.malefic.koven.core.PathProvider
import xyz.malefic.koven.core.QueryField
import xyz.malefic.koven.core.QueryProvider
import xyz.malefic.koven.core.Redirect
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.model.TokenResponseModel
import xyz.malefic.koven.feature.auth.model.UserRequestModel

/**
 * A contract for the user login endpoint.
 */
val PasswordLoginContract = apiContract<UserRequestModel, TokenResponseModel>("auth/login").build()

/**
 * A contract for the user registration endpoint.
 */
val PasswordRegisterContract = apiContract<UserRequestModel, TokenResponseModel>("auth/register").build()

/**
 * A contract for the token refresh endpoint.
 */
val RefreshContract = apiContract<Unit, TokenResponseModel>("auth/refresh").build()

/**
 * A contract for the logout endpoint.
 */
val LogoutContract = apiContract<Unit, Unit>("auth/logout").build()

/**
 * A contract for the password strength endpoint.
 */
val PasswordStrengthContract = apiContract<String, Pair<Int, String?>>("auth/strength").build()

/**
 * Path parameters for OAuth login.
 */
data class OAuthLoginPath(
    val provider: String,
) : PathProvider {
    override fun providePath() = mapOf("provider" to provider)

    companion object : PathField<OAuthLoginPath> {
        context(raise: Raise<Issue>)
        override fun decodePath(params: Map<String, String>): OAuthLoginPath {
            val provider = params["provider"] ?: raise.raise(BadRequestIssue("Missing provider"))
            return OAuthLoginPath(provider)
        }
    }
}

/**
 * A contract for the OAuth login initiation endpoint.
 */
val OAuthLoginContract =
    apiContract<Unit, Unit>("auth/login/{provider}")
        .method(GET)
        .path(OAuthLoginPath)
        .build()

/**
 * A contract for linking an OAuth provider to an existing account.
 */
val OAuthLinkContract =
    apiContract<Unit, Unit>("auth/link/{provider}")
        .method(GET)
        .path(OAuthLoginPath)
        .protected()
        .build()

/**
 * Query parameters for OAuth finalization.
 */
data class OAuthFinalizeQuery(
    val username: String? = null,
    val next: String? = null, // TODO: Protect against open redirects
    val error: String? = null,
    val provider: String? = null,
) : QueryProvider {
    override fun provideQuery() =
        buildMap {
            username?.let { put("username", listOf(it)) }
            next?.let { put("next", listOf(it)) }
            error?.let { put("error", listOf(it)) }
            provider?.let { put("provider", listOf(it)) }
        }

    companion object : QueryField<OAuthFinalizeQuery> {
        context(raise: Raise<Issue>)
        override fun decodeQuery(params: Map<String, List<String>>) =
            OAuthFinalizeQuery(
                username = params["username"]?.firstOrNull(),
                next = params["next"]?.firstOrNull(),
                error = params["error"]?.firstOrNull(),
                provider = params["provider"]?.firstOrNull(),
            )
    }
}

/**
 * A contract for the OAuth callback finalization endpoint.
 */
val OAuthFinalizeContract =
    apiContract<Unit, Unit>("auth/oauth/finalize")
        .method(GET)
        .query(OAuthFinalizeQuery, "username", "next", "error", "provider")
        .responseHeaders(Redirect)
        .build()
