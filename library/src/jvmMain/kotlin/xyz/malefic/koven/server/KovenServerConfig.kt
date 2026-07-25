package xyz.malefic.koven.server

import org.http4k.filter.CorsPolicy
import xyz.malefic.koven.util.lock

/**
 * Configuration for the [KovenServer].
 */
class KovenServerConfig {
    /**
     * The port to listen on. Defaults to `8080`.
     */
    var port: Int by lock(8080)

    /**
     * Whether to host static assets. Defaults to `true`.
     */
    var assetsHosting: Boolean by lock(true)

    /**
     * The directory to host static assets from. Defaults to `"assets"`.
     */
    var assetsPath: String by lock("assets")

    /**
     * Whether to host user files. Defaults to `true`.
     */
    var filesHosting: Boolean by lock(true)

    /**
     * The directory to host user files from. Defaults to `"files"`.
     */
    var filesPath: String by lock("files")

    /**
     * The CORS policy to use. Defaults to [CorsPolicy.UnsafeGlobalPermissive].
     */
    var corsPolicy: CorsPolicy by lock(CorsPolicy.UnsafeGlobalPermissive)
}
