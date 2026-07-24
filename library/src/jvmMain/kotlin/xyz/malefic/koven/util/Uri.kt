package xyz.malefic.koven.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Encodes a URI component using java.net.URLEncoder.
 */
actual fun encodeUriComponent(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

/**
 * Decodes a URI component using java.net.URLDecoder.
 */
actual fun decodeUriComponent(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
