package xyz.malefic.spyder

import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.serialization.JsonSerializer
import xyz.malefic.spyder.serialization.SerializationEngine

/**
 * Global configuration for the Spyder framework.
 */
object SpyderConfig {
    /**
     * The default serialization engine used by the framework. Defaults to [xyz.malefic.spyder.serialization.JsonSerializer].
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
     * Registers a new [xyz.malefic.spyder.error.Issue] subclass globally.
     */
    inline fun <reified T : Issue> registerIssue() = SerializationEngine.registerIssue<T>()
}
