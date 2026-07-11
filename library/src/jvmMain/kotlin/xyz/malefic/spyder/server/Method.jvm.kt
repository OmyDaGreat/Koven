package xyz.malefic.spyder.server

import xyz.malefic.spyder.Method
import org.http4k.core.Method as Http4kMethod

val Method.toHttp4k: Http4kMethod get() =
    when (this) {
        Method.GET -> Http4kMethod.GET
        Method.POST -> Http4kMethod.POST
        Method.PUT -> Http4kMethod.PUT
        Method.DELETE -> Http4kMethod.DELETE
        Method.OPTIONS -> Http4kMethod.OPTIONS
        Method.PATCH -> Http4kMethod.PATCH
        Method.HEAD -> Http4kMethod.HEAD
    }
