package xyz.malefic.koven.feature.auth

import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.notBlank
import io.konform.validation.constraints.pattern
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import xyz.malefic.koven.feature.auth.model.UserRequestModel

private val nbvcxz = Nbvcxz(ConfigurationBuilder().createConfiguration())

/**
 * The default validation for user registration on the JVM.
 */
actual val defaultPasswordValidation: Validation<UserRequestModel> =
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
