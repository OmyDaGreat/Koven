package xyz.malefic.spyder.feature.auth.server

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import at.favre.lib.crypto.bcrypt.BCrypt
import co.touchlab.kermit.Logger
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.notBlank
import io.konform.validation.constraints.pattern
import io.konform.validation.messagesAtPath
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.SameSite
import org.http4k.core.cookie.cookie
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.spyder.SpyderConfig
import xyz.malefic.spyder.error.AuthIssue
import xyz.malefic.spyder.error.BadRequestIssue
import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.error.UserIssue
import xyz.malefic.spyder.feature.auth.AuthType
import xyz.malefic.spyder.feature.auth.Principal
import xyz.malefic.spyder.feature.auth.model.TokenModel
import xyz.malefic.spyder.feature.auth.model.UserRequestModel
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * Server-side handler for [AuthType.Local].
 */
object LocalAuthHandler : ServerAuthHandler<AuthType.Local> {
    private val log = Logger.withTag("LocalAuth")
    private val secureRandom = SecureRandom()
    private val bcrypt = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val nbvcxz = Nbvcxz(ConfigurationBuilder().createConfiguration())

    private val jwtAlgorithm by lazy {
        val secret =
            (System.getProperty("SPYDER_SECRET") ?: System.getenv("SPYDER_SECRET"))?.let { s ->
                if (s.length >= 32) s.toByteArray() else null
            } ?: ByteArray(32).also {
                secureRandom.nextBytes(it)
                log.w { "SPYDER_SECRET is not set or too short, using random value for this session" }
            }
        Algorithm.HMAC256(secret)
    }

    private val jwtVerifier by lazy { JWT.require(jwtAlgorithm).build() }

    context(_: Raise<Issue>)
    override fun authenticate(request: Request): Principal {
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

    infix fun Response.withCookie(refreshToken: String): Response {
        val localConfig = SpyderConfig.auth as? AuthType.Local ?: return this
        return cookie(
            Cookie(
                "refresh_token",
                refreshToken,
                localConfig.refreshTokenTtl.inWholeSeconds,
                path = "/${SpyderConfig.apiPrefix}",
                httpOnly = true,
                secure = true,
                sameSite = SameSite.None,
                domain = localConfig.cookieDomain,
            ),
        )
    }

    fun hashPassword(password: String): String = bcrypt.hashToString(12, password.toCharArray())

    private fun verifyPassword(
        pw: String,
        hash: String,
    ) = verifier.verify(pw.toCharArray(), hash).verified

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun generateSecret(bytes: Int = 32) = ByteArray(bytes).also { secureRandom.nextBytes(it) }.let { base64.encode(it) }

    private fun UserEntity.createAccessToken(): String {
        val localConfig = SpyderConfig.auth as? AuthType.Local
        val ttl = localConfig?.accessTokenTtl ?: AuthType.Local().accessTokenTtl
        return JWT
            .create()
            .withSubject(id.value.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + ttl.inWholeMilliseconds))
            .sign(jwtAlgorithm)
    }

    context(_: JdbcTransaction)
    fun UserEntity.issueTokenPair(): TokenModel {
        val localConfig = SpyderConfig.auth as? AuthType.Local
        val refreshTtl = localConfig?.refreshTokenTtl ?: AuthType.Local().refreshTokenTtl
        val accessTtl = localConfig?.accessTokenTtl ?: AuthType.Local().accessTokenTtl

        val accessToken = createAccessToken()
        val secret = generateSecret()

        val entity =
            AuthTokenEntity.new {
                this.user = this@issueTokenPair
                this.secretHash = hash(secret)
                this.expiresAt = System.currentTimeMillis() + refreshTtl.inWholeMilliseconds
            }

        return TokenModel(
            accessToken = accessToken,
            refreshToken = "${entity.id.value}:$secret",
            expiresIn = accessTtl.inWholeSeconds,
        )
    }

    context(_: Raise<Issue>)
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

    context(_: Raise<Issue>)
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

    private val validateUser =
        Validation {
            UserRequestModel::username {
                notBlank()
                minLength(3) hint "Username must have at least 3 characters"
                maxLength(32) hint "Username must have at most 32 characters"
                constrain("Username must not contain spaces") { string -> !string.any { it.isWhitespace() } }
                pattern(Regex("""^[\x21-\x7E&&[^"'`\\<>/:;%&{}|\[\]]]+$""")) hint
                    "Username must use printable ASCII and cannot include spaces or these characters: \" ' \\ < > / : ; % & { } | [ ]"
            }
            UserRequestModel::password {
                notBlank()
                minLength(12) hint "Password must have at least 12 characters"
                maxLength(64) hint "Password must have at most 64 characters"
                constrain("Password is not strong enough") { nbvcxz.estimate(it).basicScore >= 3 }
            }
        }

    context(_: Raise<Issue>)
    fun UserRequestModel.register(): TokenModel =
        transaction {
            val userValidation = validateUser(this@register)
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
}
