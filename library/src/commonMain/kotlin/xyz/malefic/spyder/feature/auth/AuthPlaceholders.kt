package xyz.malefic.spyder.feature.auth

import io.konform.validation.Validation
import xyz.malefic.spyder.feature.auth.model.UserRequestModel

/**
 * The default validation for user registration.
 */
expect val defaultPasswordValidation: Validation<UserRequestModel>
