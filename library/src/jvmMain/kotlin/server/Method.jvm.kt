package xyz.malefic.spyder.server

import xyz.malefic.spyder.Method

val Method.toHttp4k: org.http4k.core.Method get() =
    when (this) {
        Method.GET -> org.http4k.core.Method.GET
        Method.POST -> org.http4k.core.Method.POST
        Method.PUT -> org.http4k.core.Method.PUT
        Method.DELETE -> org.http4k.core.Method.DELETE
        Method.OPTIONS -> org.http4k.core.Method.OPTIONS
        Method.PATCH -> org.http4k.core.Method.PATCH
        Method.HEAD -> org.http4k.core.Method.HEAD
    }
