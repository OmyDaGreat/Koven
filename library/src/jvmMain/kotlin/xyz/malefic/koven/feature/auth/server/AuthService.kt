package xyz.malefic.koven.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.http4k.core.Request
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.ApiResponse
import xyz.malefic.koven.api.ApiResponse.Companion.with
import xyz.malefic.koven.core.field.Cookie
import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.Empty
import xyz.malefic.koven.core.field.Headers
import xyz.malefic.koven.core.field.SameSite
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.error.UserIssue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.BearerAuth
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.model.TokenModel
import xyz.malefic.koven.feature.auth.model.TokenResponseModel
import xyz.malefic.koven.server.get
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * Shared service for authentication logic, including JWT handling and token issuing.
 */
object AuthService {
    private val log = Logger.withTag("AuthService")
    private val secureRandom = SecureRandom()
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /**
     * HttpOnly cookie for the refresh token.
     */
    object RefreshTokenCookie : CookieField<String> {
        override val name: String = "refresh_token"

        override fun maxAge(): Long = KovenConfig.auth.refreshTokenTtl.inWholeSeconds

        override fun path(): String = "/${KovenConfig.apiPrefix}"

        override fun domain(): String? = KovenConfig.auth.cookieDomain

        override fun secure(): Boolean = KovenConfig.auth.useSecureCookies

        override fun isHttpOnly(): Boolean = true

        override fun sameSite(): SameSite = SameSite.Lax

        /**
         * Creates a [Cookie] using the properties defined in this field.
         */
        override infix fun create(value: String): Cookie = super.create(value)

        /**
         * Creates an empty [Cookie] with the properties defined in this field for the purpose of clearing an already-existent cookie.
         */
        fun clear(unit: Unit): Cookie = super.clear()

        context(_: Raise<Issue>)
        override fun decode(cookies: Map<String, String>): String =
            ensureNotNull(cookies[name]) { AuthIssue.InvalidToken("Refresh token cookie missing") }

        override fun encodeCookies(value: String): List<Cookie> = listOf(create(value))
    }

    private val jwtAlgorithm by lazy {
        val secret =
            (System.getProperty("KOVEN_SECRET") ?: System.getenv("KOVEN_SECRET"))?.let { s ->
                if (s.length >= 32) s.toByteArray() else null
            } ?: ByteArray(32).also {
                secureRandom.nextBytes(it)
                log.w { "KOVEN_SECRET is not set or too short, using random value for this session" }
            }
        Algorithm.HMAC256(secret)
    }

    /**
     * The JWT verifier used to validate access tokens.
     */
    val jwtVerifier: JWTVerifier by lazy { JWT.require(jwtAlgorithm).build() }

    /**
     * Hashes the given [text] using SHA-256.
     */
    fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Generates a secure random secret of the given [bytes] length, encoded as Base64.
     */
    fun generateSecret(bytes: Int = 32) = ByteArray(bytes).also { secureRandom.nextBytes(it) }.let { base64.encode(it) }

    /**
     * Creates an access token for the given user.
     */
    context(auth: AuthType)
    fun UserEntity.createAccessToken(): String =
        JWT
            .create()
            .withSubject(id.value.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + auth.accessTokenTtl.inWholeMilliseconds))
            .sign(jwtAlgorithm)

    /**
     * Issues a pair of access and refresh tokens for the given user.
     */
    context(_: JdbcTransaction, auth: AuthType)
    fun UserEntity.issueTokenPair(): TokenModel {
        val accessToken = createAccessToken()
        val secret = generateSecret()

        val entity =
            AuthTokenEntity.new {
                this.user = this@issueTokenPair
                this.secretHash = hash(secret)
                this.expiresAt = System.currentTimeMillis() + auth.refreshTokenTtl.inWholeMilliseconds
            }

        return TokenModel(
            userId = id.value,
            username = username,
            accessToken = accessToken,
            refreshToken = "${entity.id.value}:$secret",
            expiresIn = auth.accessTokenTtl.inWholeSeconds,
        )
    }

    /**
     * Verifies the given [token] and returns the subject (user ID) if successful.
     */
    context(_: Raise<Issue>)
    fun verifyToken(token: String): Uuid {
        val subject =
            Either
                .catch { jwtVerifier.verify(token).subject }
                .getOrElse { raise(AuthIssue.InvalidToken(it.message ?: "Invalid token")) }

        return Uuid.parseOrNull(subject) ?: raise(AuthIssue.InvalidToken("Invalid user ID in token"))
    }

    /**
     * Shared authentication logic for both OAuth and Password handlers.
     */
    context(_: Raise<Issue>)
    fun authenticate(request: Request): Principal {
        val headers = Headers.fromPairs(request.headers)
        val token = either { BearerAuth.decode(headers) }.getOrNull()

        if (token != null) {
            val userId = verifyToken(token)

            return transaction {
                ensureNotNull(UserEntity.findById(userId)) { AuthIssue.InvalidToken("User not found") }
            }
        }

        val refreshToken = either { request[RefreshTokenCookie] }.getOrNull()

        if (refreshToken != null) {
            return verifyRefreshToken(refreshToken)
        }

        raise(AuthIssue.MissingToken())
    }

    /**
     * Verifies the given [refreshToken] and returns the associated [UserEntity] if successful.
     */
    context(_: Raise<Issue>)
    fun verifyRefreshToken(refreshToken: String): UserEntity =
        transaction {
            val parts = refreshToken.split(":")
            ensure(parts.size == 2) { BadRequestIssue("Invalid refresh token format") }
            val idPart = parts[0]
            val secret = parts[1]

            val id = ensureNotNull(Uuid.parseOrNull(idPart)) { BadRequestIssue("Invalid refresh token ID") }
            val token = ensureNotNull(AuthTokenEntity.findById(id)) { AuthIssue.InvalidToken("Refresh token not found") }
            val now = System.currentTimeMillis()

            if ((token.expiresAt < now) || (token.secretHash != hash(secret)) || (token.revokedAt != null)) {
                if (token.revokedAt == null) token.revokedAt = now
                raise(AuthIssue.InvalidToken("Refresh token expired, invalid, or already revoked"))
            }

            token.user
        }

    /**
     * Shared logic to refresh a token pair using a refresh token.
     */
    context(_: Raise<Issue>, auth: AuthType)
    fun refreshTokens(refreshToken: String): TokenModel =
        transaction {
            val user = verifyRefreshToken(refreshToken)
            val parts = refreshToken.split(":")
            val id = Uuid.parse(parts[0])
            val token = AuthTokenEntity.findById(id)!!

            token.revokedAt = System.currentTimeMillis()
            user.issueTokenPair()
        }

    /**
     * Shared logic to logout by revoking the refresh token and clearing the cookie.
     */
    context(_: Raise<Issue>)
    fun Request.logout(): Cookie {
        val req = this
        val refreshToken = either { req[RefreshTokenCookie] }.getOrNull()
        val _ = either { refreshToken?.let { logout(it) } }
        return RefreshTokenCookie.clear(Unit)
    }

    context(_: Raise<Issue>, _: AuthType)
    fun Request.refresh(): ApiResponse<TokenResponseModel, Empty> {
        val tokens = refreshTokens(this[RefreshTokenCookie])
        return tokens.response with (RefreshTokenCookie create tokens.refreshToken)
    }

    /**
     * Finds a user associated with an OAuth provider and user ID.
     */
    context(_: JdbcTransaction)
    fun findOAuthUser(
        provider: String,
        providerUserId: String,
    ): UserEntity? =
        OAuthAccountEntity
            .find { (OAuthAccounts.provider eq provider) and (OAuthAccounts.providerUserId eq providerUserId) }
            .firstOrNull()
            ?.user

    /**
     * Links a new OAuth account to an existing user or creates a new user.
     *
     * @param provider The name of the OAuth provider.
     * @param providerUserId The unique ID from the provider.
     * @param preferredUsername The username the user wants to use.
     * @param email The email address of the user.
     * @param currentPrincipal The currently authenticated principal, if any.
     *
     * @return The [UserEntity] for the user.
     */
    context(_: JdbcTransaction, _: Raise<Issue>)
    fun linkOrCreateUser(
        provider: String,
        providerUserId: String,
        preferredUsername: String,
        email: String,
        currentPrincipal: Principal? = null,
    ): UserEntity {
        findOAuthUser(provider, providerUserId)?.let { return it }

        val userByEmail = UserEntity.find { Users.email eq email }.firstOrNull()

        val user =
            if (userByEmail != null) {
                if (currentPrincipal == null || currentPrincipal.userId != userByEmail.id.value) {
                    raise(AuthIssue.AccountLinkingRequired(email))
                }
                userByEmail
            } else {
                val existingUsernameUser = UserEntity.find { Users.username eq preferredUsername }.firstOrNull()

                if (existingUsernameUser != null) {
                    raise(UserIssue.AlreadyExists())
                }
                UserEntity.new {
                    this.username = preferredUsername
                    this.email = email
                }
            }

        OAuthAccountEntity.new {
            this.user = user
            this.provider = provider
            this.providerUserId = providerUserId
        }

        return user
    }

    /**
     * Revokes the given refresh token.
     */
    context(_: Raise<Issue>)
    fun logout(refreshToken: String) =
        transaction {
            val parts = refreshToken.split(":")
            ensure(parts.size == 2) { BadRequestIssue("Invalid refresh token format") }
            val idPart = parts[0]
            val secret = parts[1]

            val id = ensureNotNull(Uuid.parseOrNull(idPart)) { BadRequestIssue("Invalid refresh token ID") }
            val token = ensureNotNull(AuthTokenEntity.findById(id)) { AuthIssue.InvalidToken("Refresh token not found") }

            if (token.secretHash == hash(secret)) {
                token.revokedAt = System.currentTimeMillis()
            }
        }
}
