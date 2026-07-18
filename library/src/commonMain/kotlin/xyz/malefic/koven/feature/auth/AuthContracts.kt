package xyz.malefic.koven.feature.auth

import xyz.malefic.koven.api.apiContract
import xyz.malefic.koven.feature.auth.model.TokenResponseModel
import xyz.malefic.koven.feature.auth.model.UserRequestModel

/**
 * A contract for the user login endpoint.
 */
val LoginContract = apiContract<UserRequestModel, TokenResponseModel>("auth/login").build()

/**
 * A contract for the user registration endpoint.
 */
val RegisterContract = apiContract<UserRequestModel, TokenResponseModel>("auth/register").build()

/**
 * A contract for the token refresh endpoint.
 */
val RefreshContract = apiContract<Unit, TokenResponseModel>("auth/refresh").build()

/**
 * A contract for the logout endpoint.
 */
val LogoutContract = apiContract<Unit, Unit>("auth/logout").build()

/**
 * A contract for the password strength endpoint.
 */
val PasswordStrengthContract = apiContract<String, Pair<Int, String?>>("auth/strength").build()
