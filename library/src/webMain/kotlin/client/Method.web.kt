package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.ApiFetcher
import xyz.malefic.spyder.Method

suspend fun ApiFetcher.call(
    method: Method,
    path: String,
) = when (method) {
    Method.GET -> get(path)
    Method.POST -> post(path)
    Method.PUT -> put(path)
    Method.DELETE -> delete(path)
    Method.OPTIONS -> options(path)
    Method.PATCH -> patch(path)
    Method.HEAD -> head(path)
}
