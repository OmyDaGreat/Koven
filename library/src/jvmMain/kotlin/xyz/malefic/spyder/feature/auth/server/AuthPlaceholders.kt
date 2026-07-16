package xyz.malefic.spyder.feature.auth.server

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.http4k.core.Request
import xyz.malefic.spyder.error.AuthIssue
import xyz.malefic.spyder.error.Issue
import xyz.malefic.spyder.feature.auth.AuthType
import xyz.malefic.spyder.feature.auth.Principal

/**
 * Placeholder handler for [AuthType.OAuth2].
 */
object OAuth2AuthHandler : ServerAuthHandler<AuthType.OAuth2> {
    context(_: Raise<Issue>)
    override fun authenticate(request: Request): Principal {
        // TODO: Implement OAuth2 authentication
        raise(AuthIssue.Unauthorized("OAuth2 authentication not implemented yet"))
    }
}

/**
 * Placeholder handler for [AuthType.WebAuthn].
 */
object WebAuthnAuthHandler : ServerAuthHandler<AuthType.WebAuthn> {
    context(_: Raise<Issue>)
    override fun authenticate(request: Request): Principal {
        // TODO: Implement WebAuthn authentication
        raise(AuthIssue.Unauthorized("WebAuthn authentication not implemented yet"))
    }
}
