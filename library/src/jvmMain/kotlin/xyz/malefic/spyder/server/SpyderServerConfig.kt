package xyz.malefic.spyder.server

import org.http4k.filter.CorsPolicy

/**
 * Configuration for the [SpyderServer].
 */
class SpyderServerConfig(
    /**
     * The port to run the server on. Default is `8080`.
     */
    var port: Int = 8080,
    /**
     * Whether to host assets. Default is `true`.
     */
    var assetsHosting: Boolean = true,
    /**
     * The path to the assets directory. Default is `"assets"`.
     *
     * When changed, [assetsPrefix] should be updated accordingly.
     */
    var assetsPath: String = "assets",
    /**
     * The URL prefix for the assets directory. Default is the same as [assetsPath]. Non-functional is [assetsHosting] is `false`.
     */
    var assetsPrefix: String = assetsPath,
    /**
     * Whether to host files. Default is `true`.
     */
    var filesHosting: Boolean = true,
    /**
     * The path to the files directory. Default is `"files"`.
     *
     * When changed, [filesPrefix] should be updated accordingly.
     */
    var filesPath: String = "files",
    /**
     * The URL prefix for the files directory. Default is the same as [filesPath]. Non-functional is [filesHosting] is `false`.
     */
    var filesPrefix: String = filesPath,
    /**
     * The CORS policy to use. Default is [CorsPolicy.UnsafeGlobalPermissive].
     */
    var corsPolicy: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive,
)
