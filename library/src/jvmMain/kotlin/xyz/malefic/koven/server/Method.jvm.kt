package xyz.malefic.koven.server

import org.http4k.core.Method
import xyz.malefic.koven.api.HttpMethod

val HttpMethod.toHttp4k
    get() =
        when (this) {
            HttpMethod.GET -> Method.GET
            HttpMethod.POST -> Method.POST
            HttpMethod.PUT -> Method.PUT
            HttpMethod.DELETE -> Method.DELETE
            HttpMethod.PATCH -> Method.PATCH
            HttpMethod.HEAD -> Method.HEAD
            HttpMethod.OPTIONS -> Method.OPTIONS
        }
