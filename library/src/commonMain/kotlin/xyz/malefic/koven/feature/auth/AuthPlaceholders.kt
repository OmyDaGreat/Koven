package xyz.malefic.koven.feature.auth

import io.konform.validation.Validation
import xyz.malefic.koven.feature.auth.model.UserRequestModel

/**
 * The default validation for user registration.
 */
expect val defaultPasswordValidation: Validation<UserRequestModel>
