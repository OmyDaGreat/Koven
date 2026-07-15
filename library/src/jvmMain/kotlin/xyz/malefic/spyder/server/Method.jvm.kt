package xyz.malefic.spyder.server

import xyz.malefic.spyder.api.HttpMethod
import org.http4k.core.Method as Http4kMethod

val HttpMethod.toHttp4k
    get() =
        when (this) {
            HttpMethod.GET -> Http4kMethod.GET
            HttpMethod.POST -> Http4kMethod.POST
            HttpMethod.PUT -> Http4kMethod.PUT
            HttpMethod.DELETE -> Http4kMethod.DELETE
            HttpMethod.PATCH -> Http4kMethod.PATCH
            HttpMethod.HEAD -> Http4kMethod.HEAD
            HttpMethod.OPTIONS -> Http4kMethod.OPTIONS
        }
