package xyz.malefic.spyder.api

import xyz.malefic.spyder.core.HeaderProvider

/**
 * A wrapper for the response body and its type-safe headers.
 */
data class ApiResponse<Res, ResH>(
    val body: Res,
    val headers: ResH,
) {
    companion object {
        infix fun <Res, ResH : HeaderProvider> Res.with(headers: ResH) = ApiResponse(this, headers)
    }
}
