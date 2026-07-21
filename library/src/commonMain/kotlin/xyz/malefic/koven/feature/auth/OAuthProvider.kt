package xyz.malefic.koven.feature.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An interface for defining OAuth2 providers.
 */
interface OAuthProvider {
    val name: String
    val authEndpoint: String
    val tokenEndpoint: String
    val userInfoEndpoint: String?
    val defaultScopes: List<String> get() = listOf("openid", "profile", "email")

    /**
     * The path to the user's unique ID in the user info JSON.
     */
    val idPath: List<String>

    /**
     * The path to the user's username in the user info JSON.
     */
    val usernamePath: List<String>

    /**
     * The path to the user's email in the user info JSON.
     */
    val emailPath: List<String>

    /**
     * Parses the user info JSON from the provider and returns a triple of (providerUserId, username, email).
     *
     * Can be overridden to provide custom parsing logic.
     */
    fun parseUserInfo(json: String): Triple<String, String, String> {
        val element = OAuthProvider.json.parseToJsonElement(json)
        val id = element.findPath(idPath)?.jsonPrimitive?.content ?: error("ID not found at path $idPath in $json")
        val username =
            element.findPath(usernamePath)?.jsonPrimitive?.content ?: error("Username not found at path $usernamePath in $json")
        val email =
            element.findPath(emailPath)?.jsonPrimitive?.content ?: error("Email not found at path $emailPath in $json")
        return Triple(id, username, email)
    }

    /**
     * Standard Google OAuth2 provider.
     */
    data object Google : OAuthProvider {
        override val name = "Google"
        override val authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth"
        override val tokenEndpoint = "https://oauth2.googleapis.com/token"
        override val userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo"
        override val idPath = listOf("sub")
        override val usernamePath = listOf("name")
        override val emailPath = listOf("email")
    }

    /**
     * Standard GitHub OAuth2 provider.
     */
    data object GitHub : OAuthProvider {
        override val name = "GitHub"
        override val authEndpoint = "https://github.com/login/oauth/authorize"
        override val tokenEndpoint = "https://github.com/login/oauth/access_token"
        override val userInfoEndpoint = "https://api.github.com/user"
        override val defaultScopes = listOf("read:user", "user:email")
        override val idPath = listOf("id")
        override val usernamePath = listOf("login")
        override val emailPath = listOf("email")
    }

    /**
     * Standard Microsoft (Entra ID) OAuth2 provider.
     */
    data class Microsoft(
        val tenant: String = "common",
    ) : OAuthProvider {
        override val name = "Microsoft"
        override val authEndpoint = "https://login.microsoftonline.com/$tenant/oauth2/v2.0/authorize"
        override val tokenEndpoint = "https://login.microsoftonline.com/$tenant/oauth2/v2.0/token"
        override val userInfoEndpoint = "https://graph.microsoft.com/v1.0/me"
        override val idPath = listOf("id")
        override val usernamePath = listOf("displayName")
        override val emailPath = listOf("mail")
    }

    /**
     * A generic OAuth2 provider that can be configured at runtime.
     */
    data class Generic(
        override val name: String,
        override val authEndpoint: String,
        override val tokenEndpoint: String,
        override val userInfoEndpoint: String?,
        override val idPath: List<String>,
        override val usernamePath: List<String>,
        override val emailPath: List<String>,
        override val defaultScopes: List<String> = listOf("openid", "profile", "email"),
    ) : OAuthProvider

    companion object {
        internal val json = Json { ignoreUnknownKeys = true }

        private fun JsonElement.findPath(path: List<String>): JsonElement? {
            var current: JsonElement? = this
            for (segment in path) {
                current = current?.jsonObject?.get(segment)
            }
            return current
        }
    }
}
