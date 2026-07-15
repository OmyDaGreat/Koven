package xyz.malefic.spyder.server

import org.http4k.core.Method as Http4kMethod
import xyz.malefic.spyder.api.Method

val Method.toHttp4k
    get() =
        when (this) {
            Method.GET -> Http4kMethod.GET
            Method.POST -> Http4kMethod.POST
            Method.PUT -> Http4kMethod.PUT
            Method.DELETE -> Http4kMethod.DELETE
            Method.PATCH -> Http4kMethod.PATCH
            Method.HEAD -> Http4kMethod.HEAD
            Method.OPTIONS -> Http4kMethod.OPTIONS
        }
