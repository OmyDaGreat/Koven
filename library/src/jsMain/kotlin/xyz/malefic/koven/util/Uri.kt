package xyz.malefic.koven.util

import com.varabyte.kobweb.browser.uri.decodeURIComponent
import com.varabyte.kobweb.browser.uri.encodeURIComponent

/**
 * Encodes a URI component using JS encodeURIComponent.
 */
actual fun encodeUriComponent(value: String): String = encodeURIComponent(value)

/**
 * Decodes a URI component using JS decodeURIComponent.
 */
actual fun decodeUriComponent(value: String): String = decodeURIComponent(value)
