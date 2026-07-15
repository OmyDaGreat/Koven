package xyz.malefic.spyder.core

import arrow.core.raise.Raise
import xyz.malefic.spyder.error.Issue

/**
 * Interface for providing path parameters on the client.
 */
interface PathProvider {
    fun providePath(): Map<String, String>
}

/**
 * Interface for decoding path parameters on the server.
 */
interface PathField<out T> {
    context(raise: Raise<Issue>)
    fun decodePath(params: Map<String, String>): T
}

/**
 * Interface for providing query parameters on the client.
 */
interface QueryProvider {
    fun provideQuery(): Map<String, List<String>>
}

/**
 * Interface for decoding query parameters on the server.
 */
interface QueryField<out T> {
    context(raise: Raise<Issue>)
    fun decodeQuery(params: Map<String, List<String>>): T
}

/**
 * Default implementation for contracts without custom parameters. Can be applied to either [QueryField] or [PathField].
 */
object NoParams : PathProvider, QueryProvider, PathField<NoParams>, QueryField<NoParams> {
    override fun providePath(): Map<String, String> = emptyMap()

    override fun provideQuery(): Map<String, List<String>> = emptyMap()

    context(raise: Raise<Issue>)
    override fun decodePath(params: Map<String, String>) = this

    context(raise: Raise<Issue>)
    override fun decodeQuery(params: Map<String, List<String>>) = this
}
