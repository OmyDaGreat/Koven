package xyz.malefic.koven

import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.serialization.JsonSerializer
import xyz.malefic.koven.serialization.SerializationEngine
import xyz.malefic.koven.util.lock

/**
 * Global configuration for the Koven framework.
 */
object KovenConfig {
    /**
     * The default serialization engine used by the framework. Defaults to [xyz.malefic.koven.serialization.JsonSerializer].
     */
    var serialization: SerializationEngine<*> by lock(JsonSerializer)

    /**
     * The prefix for all API routes. Defaults to `"api"`.
     */
    var apiPrefix: String by lock("api")

    /**
     * The prefix for all asset routes. Defaults to `"assets"`.
     */
    var assetsPrefix: String by lock("assets")

    /**
     * The prefix for all user file routes. Defaults to `"files"`.
     */
    var filesPrefix: String by lock("files")

    /**
     * The configuration for authentication. Defaults to [AuthType.NoAuth].
     */
    var auth: AuthType by lock(AuthType.NoAuth)

    /**
     * Whether to use the `secure` flag for authentication cookies. Defaults to `true`.
     */
    var useSecureCookies: Boolean by lock(true)

    /**
     * Registers a new [Issue] type with the serialization engine globally.
     */
    inline fun <reified T : Issue> registerIssue() = SerializationEngine.registerIssue<T>()
}
