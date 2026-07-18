package xyz.malefic.koven.feature.auth.server

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import xyz.malefic.koven.feature.auth.Principal
import kotlin.uuid.Uuid

/**
 * Default Exposed table for users.
 */
object Users : UuidTable("koven_users") {
    val username = varchar("username", 32).uniqueIndex()
    val hashedPassword = varchar("hashed_password", 128)
    val failedAttempts = integer("failed_attempts").default(0)
    val lockUntil = long("lock_until").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/**
 * Default Exposed entity for users.
 */
class UserEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id),
    Principal {
    companion object : UuidEntityClass<UserEntity>(Users)

    override var username by Users.username
    var hashedPassword by Users.hashedPassword
    var failedAttempts by Users.failedAttempts
    var lockUntil by Users.lockUntil
    var createdAt by Users.createdAt

    override val userId: Uuid get() = this@UserEntity.id.value
}

/**
 * Default Exposed table for authentication tokens.
 */
object AuthTokens : UuidTable("koven_auth_tokens") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val secretHash = varchar("secret_hash", 64)
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/**
 * Default Exposed entity for authentication tokens.
 */
class AuthTokenEntity(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<AuthTokenEntity>(AuthTokens)

    var user by UserEntity referencedOn AuthTokens.user
    var secretHash by AuthTokens.secretHash
    var expiresAt by AuthTokens.expiresAt
    var revokedAt by AuthTokens.revokedAt
    var createdAt by AuthTokens.createdAt
}
