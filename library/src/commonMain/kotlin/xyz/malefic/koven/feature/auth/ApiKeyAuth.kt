package xyz.malefic.koven.feature.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import xyz.malefic.koven.core.field.HeaderField
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * A header field for API key authentication.
 */
object ApiKeyAuth : HeaderField<String> {
    override val field: String = "X-API-Key"
    override val fields: List<String> = listOf(field)

    /**
     * Decodes the API key from [Headers].
     *
     * @param headers The [Headers] to decode.
     *
     * @return The API key string.
     */
    context(_: Raise<Issue>)
    override fun decode(headers: Headers): String = ensureNotNull(headers.getFirst(field)) { BadRequestIssue("Missing $field header") }

    override fun encodeHeaders(value: String): Map<String, List<String>> = mapOf(field to listOf(value))
}
