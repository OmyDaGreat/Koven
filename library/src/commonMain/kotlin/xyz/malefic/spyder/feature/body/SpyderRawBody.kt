package xyz.malefic.spyder.feature.body

/**
 * A raw body. This should not be implemented directly as it is an internal interface. Use [CustomBody] instead for custom body types.
 *
 * @property contentType The content type of the body.
 */
@PublishedApi
internal interface SpyderRawBody {
    val contentType: String

    /**
     * Encodes the body to a byte array for transmission.
     */
    fun encode(): ByteArray
}

/**
 * A custom body for the request.
 *
 * @param content The content of the body.
 * @param contentType The content type of the body.
 */
data class CustomBody(
    val content: String,
    override val contentType: String,
) : SpyderRawBody {
    override fun encode() = content.encodeToByteArray()
}
