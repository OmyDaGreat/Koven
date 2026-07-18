package xyz.malefic.koven.feature.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import xyz.malefic.koven.core.Header
import xyz.malefic.koven.core.HeaderField
import xyz.malefic.koven.core.Headers
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * An example header for bearer authentication.
 */
class BearerAuth(
    val token: String,
) : Header {
    override val field: String = Companion.field
    override val values: List<String> = listOf("Bearer $token")

    companion object : HeaderField<BearerAuth> {
        override val field: String = "Authorization"

        /**
         * Decodes the Authorization header from [Headers] into a [BearerAuth].
         *
         * @param headers The [Headers] to decode.
         *
         * @return The decoded [BearerAuth] or null if the header is missing or invalid.
         */
        context(_: Raise<Issue>)
        override fun decode(headers: Headers): BearerAuth =
            ensureNotNull(
                headers
                    .getFirst(field)
                    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.removePrefix("Bearer ")
                    ?.let { BearerAuth(it) },
            ) { BadRequestIssue("Invalid Authorization header") }
    }
}
