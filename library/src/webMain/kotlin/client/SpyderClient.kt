package xyz.malefic.spyder.client

import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.serializer
import xyz.malefic.spyder.ApiContract
import xyz.malefic.spyder.SpyderJson

/**
 * A client for the `SpyderServer`.
 */
object SpyderClient {
    suspend inline fun <reified Req, reified Res> call(
        contract: ApiContract<Req, Res>,
        request: Req,
    ): Res {
        val json =
            if (Req::class == Unit::class) {
                ""
            } else {
                SpyderJson.default.encodeToString(SpyderJson.default.serializersModule.serializer<Req>(), request)
            }

        val response = window.api.post(contract.path, bodyOf(json, "application/json"))
        val text = response.text().await()

        return if (Res::class == Unit::class) {
            Unit as Res
        } else {
            SpyderJson.default.decodeFromString(SpyderJson.default.serializersModule.serializer<Res>(), text)
        }
    }
}
