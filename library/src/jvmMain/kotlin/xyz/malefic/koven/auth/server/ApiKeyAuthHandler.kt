package xyz.malefic.koven.auth.server

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import co.touchlab.kermit.Logger
import org.http4k.core.Request
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.auth.ApiKeyAuth
import xyz.malefic.koven.auth.ApiKeyCreateContract
import xyz.malefic.koven.auth.ApiKeyListContract
import xyz.malefic.koven.auth.ApiKeyRevokeContract
import xyz.malefic.koven.auth.AuthType
import xyz.malefic.koven.auth.Principal
import xyz.malefic.koven.auth.model.ApiKeyCreatedModel
import xyz.malefic.koven.auth.model.ApiKeyInfoModel
import xyz.malefic.koven.auth.model.ApiKeyRequestModel
import xyz.malefic.koven.contract.ApiResponse
import xyz.malefic.koven.contract.field.Empty
import xyz.malefic.koven.contract.field.Headers
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.server.register
import java.security.MessageDigest
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Server-side handler for [AuthType.ApiKey], implementing [AuthHandler].
 */
object ApiKeyAuthHandler : AuthHandler<AuthType.ApiKey> {
    private val log = Logger.withTag("ApiKeyAuthHandler")

    context(auth: AuthType.ApiKey)
    override fun authRoutes(): RoutingHttpHandler =
        routes(
            ApiKeyCreateContract.register { body ->
                ApiResponse(createApiKey(body), Empty)
            },
            ApiKeyListContract.register { _ ->
                ApiResponse(listApiKeys(), Empty)
            },
            ApiKeyRevokeContract.register { _, path, _ ->
                revokeApiKey(path.keyId)
            },
        )

    /**
     * Authenticates the given [request] by looking up its [ApiKeyAuth] header, rejecting missing, unknown, revoked,
     * or expired keys, and returns the [Principal] (the key's owning user) on success.
     */
    context(auth: AuthType.ApiKey, _: Raise<Issue>)
    override fun authenticate(request: Request): Principal {
        val headers = Headers.fromPairs(request.headers)
        val rawKey = ApiKeyAuth.decode(headers)
        val prefix = rawKey.take(auth.keyPrefixLength)
        val hash = AuthService.hash(rawKey)

        return transaction {
            val entity =
                ensureNotNull(ApiKeyEntity.find { ApiKeys.prefix eq prefix }.firstOrNull()) { AuthIssue.InvalidToken("Invalid API key") }
            ensure(MessageDigest.isEqual(entity.keyHash, hash)) { AuthIssue.InvalidToken("Invalid API key") }
            ensure(entity.revokedAt == null) { AuthIssue.InvalidToken("API key has been revoked") }

            val now = System.currentTimeMillis()
            ensure(entity.expiresAt == null || entity.expiresAt!! > now) { AuthIssue.InvalidToken("API key has expired") }

            entity.lastUsedAt = now
            entity.user
        }
    }

    /**
     * Creates a new API key owned by the currently authenticated [principal]. The raw key is only ever available in
     * the returned [ApiKeyCreatedModel]; only its salted hash is persisted.
     */
    context(_: Raise<Issue>, auth: AuthType.ApiKey, principal: Principal)
    fun createApiKey(request: ApiKeyRequestModel): ApiKeyCreatedModel =
        transaction {
            val user = ensureNotNull(UserEntity.findById(principal.userId)) { AuthIssue.InvalidToken("User not found") }

            var rawKey: String
            var prefix: String
            var attempts = 0
            do {
                rawKey = "${KovenConfig.globalPrefix}_${AuthService.generateSecret()}"
                prefix = rawKey.take(auth.keyPrefixLength)
                val exists = ApiKeyEntity.find { ApiKeys.prefix eq prefix }.any()
                attempts++
            } while (exists && attempts < 10)

            ensure(!ApiKeyEntity.find { ApiKeys.prefix eq prefix }.any()) {
                log.e { "Failed to generate a unique API key prefix after $attempts attempts" }
                AuthIssue.InvalidToken("Internal error generating API key")
            }

            val expiresAt =
                request.expiresInDays?.let { System.currentTimeMillis() + it.days.inWholeMilliseconds }
                    ?: auth.defaultTtl?.let { System.currentTimeMillis() + it.inWholeMilliseconds }

            val entity =
                ApiKeyEntity.new {
                    this.user = user
                    this.name = request.name
                    this.prefix = prefix
                    this.keyHash = AuthService.hash(rawKey)
                    this.expiresAt = expiresAt
                }

            ApiKeyCreatedModel(
                id = entity.id.value,
                name = entity.name,
                key = rawKey,
                prefix = prefix,
                expiresAt = expiresAt,
            )
        }

    /**
     * Lists the API keys owned by the currently authenticated [principal], excluding the raw key values.
     */
    context(principal: Principal)
    fun listApiKeys(): List<ApiKeyInfoModel> =
        transaction {
            ApiKeyEntity
                .find { ApiKeys.user eq principal.userId }
                .map {
                    ApiKeyInfoModel(
                        it.id.value,
                        it.name,
                        it.prefix,
                        it.createdAt.toEpochMilliseconds(),
                        it.expiresAt,
                        it.lastUsedAt,
                        it.revokedAt != null,
                    )
                }
        }

    /**
     * Revokes the API key identified by [keyId], provided it is owned by the currently authenticated [principal].
     */
    context(_: Raise<Issue>, principal: Principal)
    fun revokeApiKey(keyId: String) {
        transaction {
            val id = ensureNotNull(Uuid.parseOrNull(keyId)) { BadRequestIssue("Invalid API key ID") }
            val entity = ensureNotNull(ApiKeyEntity.findById(id)) { BadRequestIssue("API key not found") }
            ensure(entity.user.id.value == principal.userId) { BadRequestIssue("API key not found") }

            if (entity.revokedAt == null) entity.revokedAt = System.currentTimeMillis()
        }
    }
}
