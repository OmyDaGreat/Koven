package xyz.malefic.koven.util

/**
 * Encodes a URI component.
 */
expect fun encodeUriComponent(value: String): String

/**
 * Decodes a URI component.
 */
expect fun decodeUriComponent(value: String): String
