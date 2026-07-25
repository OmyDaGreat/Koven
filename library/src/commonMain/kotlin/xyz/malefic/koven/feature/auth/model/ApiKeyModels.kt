package xyz.malefic.koven.feature.auth.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A request to create a new API key.
 *
 * @property name A human-readable label for the key.
 * @property expiresInDays How many days until the key expires. Falls back to [xyz.malefic.koven.feature.auth.AuthType.ApiKey.defaultTtl] when null.
 */
@Serializable
data class ApiKeyRequestModel(
    val name: String,
    val expiresInDays: Long? = null,
)

/**
 * The response returned when a new API key is created.
 *
 * The raw [key] is only ever returned here, at creation time; it cannot be recovered afterward.
 */
@Serializable
data class ApiKeyCreatedModel(
    val id: Uuid,
    val name: String,
    val key: String,
    val prefix: String,
    val expiresAt: Long? = null,
)

/**
 * Metadata about an existing API key, deliberately excluding the raw key.
 */
@Serializable
data class ApiKeyInfoModel(
    val id: Uuid,
    val name: String,
    val prefix: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val lastUsedAt: Long? = null,
    val revoked: Boolean = false,
)
