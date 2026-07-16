package xyz.malefic.spyder.feature.auth

import kotlin.uuid.Uuid

/**
 * Represents an authenticated user (principal) in the system.
 */
interface Principal {
    /**
     * The unique identifier for the user.
     */
    val userId: Uuid

    /**
     * The username of the user.
     */
    val username: String
}

/**
 * A simple implementation of [Principal].
 */
data class SimplePrincipal(
    override val userId: Uuid,
    override val username: String,
) : Principal
