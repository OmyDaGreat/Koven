package xyz.malefic.koven.feature.auth.server

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import at.favre.lib.crypto.bcrypt.BCrypt
import co.touchlab.kermit.Logger
import io.konform.validation.messagesAtPath
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.routes
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.koven.api.ApiResponse.Companion.with
import xyz.malefic.koven.error.AuthIssue
import xyz.malefic.koven.error.Issue
import xyz.malefic.koven.error.UserIssue
import xyz.malefic.koven.feature.auth.AuthType
import xyz.malefic.koven.feature.auth.LogoutContract
import xyz.malefic.koven.feature.auth.PasswordLoginContract
import xyz.malefic.koven.feature.auth.PasswordRegisterContract
import xyz.malefic.koven.feature.auth.PasswordStrengthContract
import xyz.malefic.koven.feature.auth.RefreshContract
import xyz.malefic.koven.feature.auth.model.TokenModel
import xyz.malefic.koven.feature.auth.model.UserRequestModel
import xyz.malefic.koven.feature.auth.server.AuthService.issueTokenPair
import xyz.malefic.koven.server.register
import java.security.SecureRandom

/**
 * Server-side handler for [AuthType.Password], implementing [AuthHandler].
 */
object PasswordAuthHandler : AuthHandler<AuthType.Password> {
    private val log = Logger.withTag("PasswordAuth")
    private val secureRandom = SecureRandom()
    private val bcrypt = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()
    private val nbvcxz = Nbvcxz(ConfigurationBuilder().createConfiguration())

    fun String.strength(): Pair<Int, String?> = with(nbvcxz.estimate(this)) { basicScore to feedback.warning }

    context(auth: AuthType.Password)
    override fun authRoutes(): RoutingHttpHandler =
        routes(
            PasswordStrengthContract.register { string, _, _ ->
                string.strength()
            },
            PasswordLoginContract.register { body, _, _ ->
                val tokens = getTokensFromLogin(body)
                tokens.response with (AuthService.RefreshTokenCookie create tokens.refreshToken)
            },
            PasswordRegisterContract.register { body, _, _ ->
                val tokens = body.register()
                tokens.response with (AuthService.RefreshTokenCookie create tokens.refreshToken)
            },
            RefreshContract.register { _, _, _ ->
                with(AuthService) { refresh() }
            },
            LogoutContract.register { _, _, _ ->
                with(AuthService) { logout() }
            },
        )

    fun hashPassword(password: String): String = bcrypt.hashToString(12, password.toCharArray())

    private fun verifyPassword(
        pw: String,
        hash: String,
    ) = verifier.verify(pw.toCharArray(), hash).verified

    context(_: Raise<Issue>, auth: AuthType.Password)
    fun getTokensFromLogin(user: UserRequestModel): TokenModel =
        transaction {
            val userEntity =
                ensureNotNull(UserEntity.find { Users.username eq user.username }.firstOrNull()) { AuthIssue.InvalidCredentials() }
            val now = System.currentTimeMillis()

            ensure(userEntity.lockUntil < now) { AuthIssue.AccountLocked(userEntity.lockUntil) }

            ensure(userEntity.hashedPassword != null && verifyPassword(user.password, userEntity.hashedPassword!!)) {
                userEntity.failedAttempts += 1
                if (userEntity.failedAttempts >= auth.maxFailedAttempts) {
                    userEntity.lockUntil = now + auth.lockOutDuration.inWholeMilliseconds
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
            ensure(UserEntity.find { (Users.username eq username) or (Users.email eq email) }.empty()) { UserIssue.AlreadyExists() }
            UserEntity
                .new {
                    username = this@register.username
                    this.email = this@register.email
                    hashedPassword = hashPassword(password)
                }.issueTokenPair()
        }
}
