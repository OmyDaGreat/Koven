package xyz.malefic.koven.feature.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
data class SimplePrincipal(
    @SerialName("user_id") override val userId: Uuid,
    override val username: String,
) : Principal
