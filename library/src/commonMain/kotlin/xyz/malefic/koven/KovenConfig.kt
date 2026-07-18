package xyz.malefic.koven

import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.serialization.JsonSerializer
import xyz.malefic.koven.serialization.SerializationEngine

/**
 * Global configuration for the Koven framework.
 */
object KovenConfig {
    /**
     * The default serialization engine used by the framework. Defaults to [xyz.malefic.koven.serialization.JsonSerializer].
     */
    var serialization: SerializationEngine<*> = JsonSerializer

    /**
     * The prefix for all API routes. Defaults to `"api"`.
     */
    var apiPrefix: String = "api"

    /**
     * The prefix for all asset routes. Defaults to `"assets"`.
     */
    var assetsPrefix: String = "assets"

    /**
     * The prefix for all user file routes. Defaults to `"files"`.
     */
    var filesPrefix: String = "files"

    /**
     * The configuration for authentication. Defaults to [AuthType.NoAuth].
     */
    var auth: AuthType = AuthType.NoAuth

    /**
     * Registers a new [xyz.malefic.koven.error.Issue] subclass globally.
     */
    inline fun <reified T : Issue> registerIssue() = SerializationEngine.registerIssue<T>()
}
