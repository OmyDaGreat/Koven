package xyz.malefic.koven.feature.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
) {
    /**
     * Converts this token pair into a response model that only includes the access token.
     */
    val response = TokenResponseModel(accessToken, expiresIn)
}

/**
 * A model representing a token response to be sent to the client.
 */
@Serializable
data class TokenResponseModel(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)
