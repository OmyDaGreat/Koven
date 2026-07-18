package xyz.malefic.koven.feature.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.malefic.koven.feature.auth.Principal
import kotlin.uuid.Uuid

/**
 * A standard request model for user authentication.
 */
@Serializable
data class UserRequestModel(
    val username: String,
    val password: String,
)

/**
 * A model representing a pair of authentication tokens.
 */
@Serializable
data class TokenModel(
    @SerialName("user_id") override val userId: Uuid,
    override val username: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
) : Principal {
    /**
     * Converts this token pair into a [TokenResponseModel] that only includes the access token and [Principal] fields.
     */
    val response = TokenResponseModel(userId, username, accessToken, expiresIn)
}

/**
 * A model representing a token response and [Principal] to be sent to the client.
 */
@Serializable
data class TokenResponseModel(
    @SerialName("user_id") override val userId: Uuid,
    override val username: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
) : Principal
