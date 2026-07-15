package xyz.malefic.spyder.feature.body.binary

import xyz.malefic.spyder.feature.body.SpyderRawBody

/**
 * A binary body.
 *
 * @param data The content of the body.
 * @param contentType The content type of the body. Defaults to `"application/octet-stream"`.
 */
data class BinaryBody(
    val data: ByteArray,
    override val contentType: String = "application/octet-stream",
) : SpyderRawBody {
    override fun encode() = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryBody) return false
        return data.contentEquals(other.data) && contentType == other.contentType
    }

    override fun hashCode(): Int = 31 * data.contentHashCode() + contentType.hashCode()
}
