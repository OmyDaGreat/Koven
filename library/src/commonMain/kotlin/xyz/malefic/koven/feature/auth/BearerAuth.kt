package xyz.malefic.koven.feature.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import xyz.malefic.koven.core.field.HeaderField
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * An example header for bearer authentication.
 */
object BearerAuth : HeaderField<String> {
    override val field: String = "Authorization"
    override val fields: List<String> = listOf(field)

    /**
     * Decodes the Authorization header from [Headers] into a token string.
     *
     * @param headers The [Headers] to decode.
     *
     * @return The decoded token string.
     */
    context(_: Raise<Issue>)
    override fun decode(headers: Headers): String =
        ensureNotNull(
            headers
                .getFirst(field)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.removePrefix("Bearer "),
        ) { BadRequestIssue("Invalid Authorization header") }

    override fun encodeHeaders(value: String): Map<String, List<String>> = mapOf(field to listOf("Bearer $value"))
}
