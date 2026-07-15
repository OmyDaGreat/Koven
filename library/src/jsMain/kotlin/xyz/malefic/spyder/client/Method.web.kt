package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.ApiFetcher
import com.varabyte.kobweb.browser.http.RequestBody
import xyz.malefic.spyder.api.Method
import xyz.malefic.spyder.core.Headers

suspend fun ApiFetcher.call(
    method: Method,
    path: String,
    body: RequestBody? = null,
    headers: Headers = Headers(),
) = when (method) {
    Method.GET -> get(path, headers)
    Method.POST -> post(path, body, headers)
    Method.PUT -> put(path, body, headers)
    Method.DELETE -> delete(path, headers)
    Method.OPTIONS -> options(path, headers)
    Method.PATCH -> patch(path, body, headers)
    Method.HEAD -> head(path, headers)
}
