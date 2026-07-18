package xyz.malefic.koven.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import arrow.core.raise.either
import at.favre.lib.crypto.bcrypt.BCrypt
import co.touchlab.kermit.Logger
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.konform.validation.messagesAtPath
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import org.http4k.core.Request
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.koven.KovenConfig
import xyz.malefic.koven.api.ApiResponse.Companion.with
import xyz.malefic.koven.core.Cookie
import xyz.malefic.koven.core.CookieField
import xyz.malefic.koven.core.SameSite
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.error.UserIssue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LoginContract
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.PasswordStrengthContract
import xyz.malefic.koven.feature.auth.Principal
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.feature.auth.RegisterContract
import xyz.malefic.koven.feature.auth.model.TokenModel
import xyz.malefic.koven.feature.auth.model.UserRequestModel
import xyz.malefic.koven.server.get
import xyz.malefic.koven.server.register
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * Server-side handler for [AuthType.Password], implementing [AuthHandler].
 */
object PasswordAuthHandler : AuthHandler<AuthType.Password> {
    private val log = Logger.withTag("PasswordAuth")
    private val secureRandom = SecureRandom()
    private val bcrypt = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val nbvcxz = Nbvcxz(ConfigurationBuilder().createConfiguration())

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

    private val jwtVerifier by lazy { JWT.require(jwtAlgorithm).build() }

    /**
     * HttpOnly cookie for the refresh token.
     */
    object RefreshTokenCookie : CookieField<String> {
        var auth: AuthType.Password? = null

        override val name: String = "refresh_token"

        override fun maxAge(): Long? = auth?.refreshTokenTtl?.inWholeSeconds

        override fun path(): String = "/${KovenConfig.apiPrefix}"

        override fun domain(): String? = auth?.cookieDomain

        override fun secure(): Boolean = true

        override fun isHttpOnly(): Boolean = true

        override fun sameSite(): SameSite = SameSite.None

        /**
         * Creates a [Cookie] using the properties defined in this field, with an [AuthType.Password] context for safety.
         */
        context(auth: AuthType.Password)
        infix fun create(token: String): Cookie = super.create(token.also { this.auth = auth })

        /**
         * Creates an empty [Cookie] with the properties defined in this field for the purpose of clearing an already-existent cookie, with an [AuthType.Password] context for safety.
         */
        context(auth: AuthType.Password)
        infix fun clear(unit: Unit): Cookie {
            this.auth = auth
            return super.clear()
        }

        context(_: Raise<Issue>)
        override fun decode(cookies: Map<String, String>): String =
            ensureNotNull(cookies[name]) { AuthIssue.InvalidToken("Refresh token cookie missing") }
    }

    fun String.strength(): Pair<Int, String?> = with(nbvcxz.estimate(this)) { basicScore to feedback.warning }

    context(auth: AuthType.Password)
    override fun authRoutes(): RoutingHttpHandler =
        routes(
            PasswordStrengthContract.register { string ->
                string.strength()
            },
            LoginContract.register { body ->
                val tokens = getTokensFromLogin(body)
                tokens.response with RefreshTokenCookie.create(token = tokens.refreshToken)
            },
            RegisterContract.register { body ->
                val tokens = body.register()
                tokens.response with RefreshTokenCookie.create(token = tokens.refreshToken)
            },
            RefreshContract.register { _ ->
                val tokens = refreshTokens(this[RefreshTokenCookie])
                tokens.response with RefreshTokenCookie.create(token = tokens.refreshToken)
            },
            LogoutContract.register { _ ->
                val req = this
                val refreshToken = either { req[RefreshTokenCookie] }.getOrNull()
                if (refreshToken != null) logout(refreshToken)
                RefreshTokenCookie.clear(Unit)
            },
        )

    context(auth: AuthType.Password, _: Raise<Issue>)
    override fun authenticate(request: Request): Principal {
        if (RefreshTokenCookie.auth == null) RefreshTokenCookie.auth = auth

        val token =
            request
                .header("Authorization")
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.removePrefix("Bearer ")
                ?.trim()
                ?: request.query("token")
                ?: raise(AuthIssue.MissingToken())

        val subject =
            Either
                .catch { jwtVerifier.verify(token).subject }
                .getOrElse { raise(AuthIssue.InvalidToken(it.message ?: "Invalid token")) }

        val userId = Uuid.parseOrNull(subject) ?: raise(AuthIssue.InvalidToken("Invalid user ID in token"))

        return transaction {
            ensureNotNull(UserEntity.findById(userId)) { AuthIssue.InvalidToken("User not found") }
        }
    }

    fun hashPassword(password: String): String = bcrypt.hashToString(12, password.toCharArray())

    private fun verifyPassword(
        pw: String,
        hash: String,
    ) = verifier.verify(pw.toCharArray(), hash).verified

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun generateSecret(bytes: Int = 32) = ByteArray(bytes).also { secureRandom.nextBytes(it) }.let { base64.encode(it) }

    context(auth: AuthType.Password)
    private fun UserEntity.createAccessToken(): String =
        JWT
            .create()
            .withSubject(id.value.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + auth.accessTokenTtl.inWholeMilliseconds))
            .sign(jwtAlgorithm)

    context(_: JdbcTransaction, auth: AuthType.Password)
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

    context(_: Raise<Issue>, auth: AuthType.Password)
    fun refreshTokens(refreshToken: String): TokenModel =
        transaction {
            val parts = refreshToken.split(":")
            ensure(parts.size == 2) { BadRequestIssue("Invalid refresh token format") }
            val idPart = parts[0]
            val secret = parts[1]

            val id = ensureNotNull(Uuid.parseOrNull(idPart)) { BadRequestIssue("Invalid refresh token ID") }
            val token = ensureNotNull(AuthTokenEntity.findById(id)) { AuthIssue.InvalidToken("Refresh token not found") }
            val now = System.currentTimeMillis()

            if (token.expiresAt < now || token.secretHash != hash(secret) || token.revokedAt != null) {
                token.revokedAt = now
                raise(AuthIssue.InvalidToken("Refresh token expired, invalid, or already revoked"))
            }

            token.revokedAt = now
            token.user.issueTokenPair()
        }

    context(_: Raise<Issue>, auth: AuthType.Password)
    fun getTokensFromLogin(user: UserRequestModel): TokenModel =
        transaction {
            val userEntity =
                ensureNotNull(UserEntity.find { Users.username eq user.username }.firstOrNull()) { AuthIssue.InvalidCredentials() }
            val now = System.currentTimeMillis()

            ensure(userEntity.lockUntil < now) { AuthIssue.AccountLocked(userEntity.lockUntil) }

            ensure(verifyPassword(user.password, userEntity.hashedPassword)) {
                userEntity.failedAttempts += 1
                if (userEntity.failedAttempts >= 5) { // MAX_FAILED_ATTEMPTS
                    userEntity.lockUntil = now + (15 * 60 * 1000) // LOCKOUT_DURATION (15 min)
                }
                AuthIssue.InvalidCredentials()
            }

            userEntity.failedAttempts = 0
            userEntity.lockUntil = 0
            userEntity.issueTokenPair()
        }

    context(_: Raise<Issue>, auth: AuthType.Password)
    fun UserRequestModel.register(): TokenModel =
        transaction {
            val userValidation = auth.validation(this@register)
            ensure(userValidation.isValid) {
                UserIssue.InvalidUser(
                    userValidation.errors.messagesAtPath(UserRequestModel::username),
                    userValidation.errors.messagesAtPath(UserRequestModel::password),
                )
            }
            ensure(UserEntity.find { Users.username eq username }.empty()) { UserIssue.AlreadyExists() }
            UserEntity
                .new {
                    username = this@register.username
                    hashedPassword = hashPassword(password)
                }.issueTokenPair()
        }

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
