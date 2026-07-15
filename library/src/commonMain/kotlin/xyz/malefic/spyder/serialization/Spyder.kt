package xyz.malefic.spyder.serialization

import xyz.malefic.spyder.error.Issue

/**
 * Global configuration for the Spyder framework.
 */
object Spyder {
    /**
     * The default serialization engine used by the framework. Defaults to [SpyderJson].
     */
    var serialization: Serializer<*> = SpyderJson

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
     * Registers a new [Issue] subclass globally.
     */
    inline fun <reified T : Issue> registerIssue() = Serializer.registerIssue<T>()
}
