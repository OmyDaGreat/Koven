package xyz.malefic.spyder.server

import org.http4k.filter.CorsPolicy

/**
 * Configuration for the [SpyderServer].
 */
class SpyderServerConfig {
    /**
     * The port to listen on. Defaults to `8080`.
     */
    var port: Int = 8080

    /**
     * Whether to host static assets. Defaults to `true`.
     */
    var assetsHosting: Boolean = true

    /**
     * The directory to host static assets from. Defaults to `"assets"`.
     */
    var assetsPath: String = "assets"

    /**
     * Whether to host user files. Defaults to `true`.
     */
    var filesHosting: Boolean = true

    /**
     * The directory to host user files from. Defaults to `"files"`.
     */
    var filesPath: String = "files"

    /**
     * The CORS policy to use. Defaults to [CorsPolicy.UnsafeGlobalPermissive].
     */
    var corsPolicy: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive
}
