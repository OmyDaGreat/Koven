package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.ApiFetcher
import com.varabyte.kobweb.browser.http.RequestBody
import xyz.malefic.spyder.api.HttpMethod
import xyz.malefic.spyder.core.Headers

suspend fun ApiFetcher.call(
    httpMethod: HttpMethod,
    path: String,
    body: RequestBody? = null,
    headers: Headers = Headers(),
) = when (httpMethod) {
    HttpMethod.GET -> get(path, headers)
    HttpMethod.POST -> post(path, body, headers)
    HttpMethod.PUT -> put(path, body, headers)
    HttpMethod.DELETE -> delete(path, headers)
    HttpMethod.OPTIONS -> options(path, headers)
    HttpMethod.PATCH -> patch(path, body, headers)
    HttpMethod.HEAD -> head(path, headers)
}
