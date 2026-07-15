package xyz.malefic.spyder.feature.body.text

import xyz.malefic.spyder.feature.body.SpyderRawBody

/**
 * A plain text body.
 *
 * @param text The content of the body.
 */
data class PlainTextBody(
    val text: String,
) : SpyderRawBody {
    override val contentType = "text/plain"

    override fun encode() = text.encodeToByteArray()
}
