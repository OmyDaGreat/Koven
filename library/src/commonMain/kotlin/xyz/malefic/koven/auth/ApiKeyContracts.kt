package xyz.malefic.koven.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import xyz.malefic.koven.auth.model.ApiKeyCreatedModel
import xyz.malefic.koven.auth.model.ApiKeyInfoModel
import xyz.malefic.koven.auth.model.ApiKeyRequestModel
import xyz.malefic.koven.contract.HttpMethod.DELETE
import xyz.malefic.koven.contract.HttpMethod.GET
import xyz.malefic.koven.contract.apiContract
import xyz.malefic.koven.contract.field.PathField
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue

/**
 * A contract for creating a new API key for the currently authenticated principal.
 */
val ApiKeyCreateContract = apiContract<ApiKeyRequestModel, ApiKeyCreatedModel>("auth/keys").protected().build()

/**
 * A contract for listing the API keys owned by the currently authenticated principal.
 */
val ApiKeyListContract = apiContract<Unit, List<ApiKeyInfoModel>>("auth/keys").method(GET).protected().build()

/**
 * Path parameters identifying a single API key.
 */
data class ApiKeyIdPath(
    val keyId: String,
) {
    fun providePath() = mapOf("keyId" to keyId)

    companion object : PathField<ApiKeyIdPath> {
        override val fields: List<String> = listOf("keyId")

        context(_: Raise<Issue>)
        override fun decode(params: Map<String, String>): ApiKeyIdPath {
            val keyId = ensureNotNull(params["keyId"]) { BadRequestIssue("Missing keyId") }
            return ApiKeyIdPath(keyId)
        }

        override fun encodePath(value: ApiKeyIdPath): Map<String, String> = value.providePath()
    }
}

/**
 * A contract for revoking an API key owned by the currently authenticated principal.
 */
val ApiKeyRevokeContract =
    apiContract<Unit, Unit>("auth/keys/{keyId}")
        .method(DELETE)
        .path(ApiKeyIdPath)
        .protected()
        .build()
