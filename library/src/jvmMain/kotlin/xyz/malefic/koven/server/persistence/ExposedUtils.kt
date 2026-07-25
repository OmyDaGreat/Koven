package xyz.malefic.koven.server.persistence

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.error.UserIssue
import java.sql.SQLIntegrityConstraintViolationException

/**
 * Executes the given [block] and catches [ExposedSQLException] to handle unique constraint violations.
 *
 * If a unique constraint violation is detected (SQLState starts with "23"), it raises [UserIssue.AlreadyExists].
 */
@IgnorableReturnValue
context(_: Raise<Issue>)
inline fun <T> ensureUnique(block: () -> T): T =
    try {
        block()
    } catch (e: ExposedSQLException) {
        if (e.cause is SQLIntegrityConstraintViolationException || e.sqlState.startsWith("23")) {
            raise(UserIssue.AlreadyExists())
        } else {
            throw e
        }
    }
