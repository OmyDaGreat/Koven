package xyz.malefic.spyder.feature.body.xml

import xyz.malefic.spyder.feature.body.SpyderRawBody

/**
 * An XML body.
 *
 * @param xml The content of the body.
 */
data class XmlBody(
    val xml: String,
) : SpyderRawBody {
    override val contentType = "application/xml"

    override fun encode() = xml.encodeToByteArray()
}
