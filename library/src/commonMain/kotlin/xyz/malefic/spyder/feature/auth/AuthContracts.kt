package xyz.malefic.spyder.feature.auth

import xyz.malefic.spyder.api.apiContract
import xyz.malefic.spyder.core.AnyHeaders
import xyz.malefic.spyder.feature.auth.model.TokenResponseModel
import xyz.malefic.spyder.feature.auth.model.UserRequestModel

/**
 * A contract for the user login endpoint.
 */
val LoginContract =
    apiContract<UserRequestModel, TokenResponseModel>("auth/login")
        .responseHeaders(AnyHeaders)
        .build()

/**
 * A contract for the user registration endpoint.
 */
val RegisterContract =
    apiContract<UserRequestModel, TokenResponseModel>("auth/register")
        .responseHeaders(AnyHeaders)
        .build()

/**
 * A contract for the token refresh endpoint.
 */
val RefreshContract =
    apiContract<Unit, TokenResponseModel>("auth/refresh")
        .responseHeaders(AnyHeaders)
        .build()

/**
 * A contract for the logout endpoint.
 */
val LogoutContract =
    apiContract<Unit, Unit>("auth/logout")
        .responseHeaders(AnyHeaders)
        .build()
