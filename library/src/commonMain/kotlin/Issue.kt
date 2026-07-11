package xyz.malefic.spyder

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable

/**
 * A base class for all API issues.
 * Shared between client and server for type-safe error handling.
 *
 * @property message The error message.
 * @property status The HTTP status code. Should be something supported by http4k.
 *
 * @see <a href=https://github.com/http4k/http4k/blob/master/core/core/src/main/kotlin/org/http4k/core/Status.kt#L5>http4k Status</a>
 */
@Serializable
abstract class Issue {
    abstract val message: String
    abstract val status: Short
}

/**
 * Common issue for internal server errors.
 */
@Serializable
data class InternalIssue(
    override val message: String = "Internal Server Error",
    override val status: Short = 500,
) : Issue() {
    companion object {
        infix fun from(e: Throwable): InternalIssue {
            Logger.e(e) { "Internal Server Error: ${e.message}" }
            return InternalIssue(e.message ?: "Internal Server Error")
        }
    }
}

/**
 * Common issue for validation or bad request errors.
 */
@Serializable
data class BadRequestIssue(
    override val message: String = "Bad Request",
    override val status: Short = 400,
) : Issue()
