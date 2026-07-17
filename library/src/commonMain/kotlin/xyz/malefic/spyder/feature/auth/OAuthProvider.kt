package xyz.malefic.spyder.feature.auth

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
     * Standard Google OAuth2 provider.
     */
    object Google : OAuthProvider {
        override val name = "Google"
        override val authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth"
        override val tokenEndpoint = "https://oauth2.googleapis.com/token"
        override val userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo"
    }

    /**
     * Standard GitHub OAuth2 provider.
     */
    object GitHub : OAuthProvider {
        override val name = "GitHub"
        override val authEndpoint = "https://github.com/login/oauth/authorize"
        override val tokenEndpoint = "https://github.com/login/oauth/access_token"
        override val userInfoEndpoint = "https://api.github.com/user"
        override val defaultScopes = listOf("read:user", "user:email")
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
    }
}
