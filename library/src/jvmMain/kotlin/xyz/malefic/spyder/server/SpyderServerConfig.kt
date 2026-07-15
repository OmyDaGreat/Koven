package xyz.malefic.spyder.server

import org.http4k.filter.CorsPolicy

/**
 * Configuration for the [SpyderServer].
 *
 * @property port The port to listen on. Default is 8080.
 * @property assetsHosting Whether to host static assets. Default is true.
 * @property assetsPath The directory to host static assets from. Default is "assets".
 * @property assetsPrefix The prefix for asset URLs. Default is "assets".
 * @property filesHosting Whether to host user files. Default is true.
 * @property filesPath The directory to host user files from. Default is "files".
 * @property filesPrefix The prefix for user file URLs. Default is "files".
 * @property corsPolicy The CORS policy to use. Default allows all origins and common methods.
 */
class SpyderServerConfig {
    var port: Int = 8080
    var assetsHosting: Boolean = true
    var assetsPath: String = "assets"
    var assetsPrefix: String = "assets"
    var filesHosting: Boolean = true
    var filesPath: String = "files"
    var filesPrefix: String = "files"
    var corsPolicy: CorsPolicy = CorsPolicy.UnsafeGlobalPermissive
}
