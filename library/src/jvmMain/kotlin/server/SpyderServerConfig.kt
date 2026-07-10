package xyz.malefic.spyder.server

import org.http4k.filter.CorsPolicy

/**
 * Configuration for the [SpyderServer].
 */
data class SpyderServerConfig(
    /**
     * The port to run the server on.
     */
    var port: Int = 8080,
    /**
     * The path to the assets directory.
     */
    var assetsPath: String = "assets",
    /**
     * The CORS policy to use.
     */
    var corsPolicy: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive,
)
