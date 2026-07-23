package xyz.malefic.koven.core.field

import arrow.core.raise.Raise
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import xyz.malefic.koven.error.BadRequestIssue
import xyz.malefic.koven.error.Issue
import kotlin.jvm.JvmName

/**
 * Interface for providing path parameters on the client.
 */
interface PathProvider : KovenProvider {
    fun providePath(): Map<String, String>
}

/**
 * Interface for decoding path parameters on the server.
 */
interface PathField<out T> : KovenField {
    context(_: Raise<Issue>)
    fun decodePath(params: Map<String, String>): T

    /**
     * The field names for this path parameter.
     */
    override val fields: List<String>

    /**
     * Flattens the [PathField] into its constituent fields.
     */
    override fun flatten(): List<PathField<*>> = listOf(this)

    companion object {
        operator fun invoke(name: String): PathField<String> =
            object : PathField<String> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodePath(params: Map<String, String>): String =
                    ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
            }

        fun int(name: String): PathField<Int> =
            object : PathField<Int> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodePath(params: Map<String, String>): Int {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toIntOrNull() ?: raise(BadRequestIssue("Invalid integer for path parameter: $name"))
                }
            }

        fun long(name: String): PathField<Long> =
            object : PathField<Long> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodePath(params: Map<String, String>): Long {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toLongOrNull() ?: raise(BadRequestIssue("Invalid long for path parameter: $name"))
                }
            }

        fun boolean(name: String): PathField<Boolean> =
            object : PathField<Boolean> {
                override val fields: List<String> = listOf(name)

                context(_: Raise<Issue>)
                override fun decodePath(params: Map<String, String>): Boolean {
                    val value = ensureNotNull(params[name]) { BadRequestIssue("Missing required path parameter: $name") }
                    return value.toBooleanStrictOrNull() ?: raise(BadRequestIssue("Invalid boolean for path parameter: $name"))
                }
            }
    }
}

/**
 * Interface for a single path parameter.
 */
interface PathParam : PathProvider {
    val field: String
    val value: String

    override fun providePath(): Map<String, String> = mapOf(field to value)
}

/**
 * Optimizes path implementations for [Empty] on the left.
 */
@JvmName("andEmptyPathProviderLeft")
infix fun <B : PathProvider> Empty.and(other: B): B = other

/**
 * Optimizes path implementations for [Empty] on the right.
 */
@JvmName("andEmptyPathProviderRight")
infix fun <A : PathProvider> A.and(other: Empty): A = this

/**
 * A path field that combines two other path fields.
 */
class PathPairField<out A, out B>(
    override val fieldA: PathField<A>,
    override val fieldB: PathField<B>,
) : KovenPairField(fieldA, fieldB),
    PathField<KovenPair<A, B>> {
    override val fields: List<String> get() = super<KovenPairField>.fields

    @Suppress("UNCHECKED_CAST")
    override fun flatten(): List<PathField<*>> = super<KovenPairField>.flatten() as List<PathField<*>>

    context(_: Raise<Issue>)
    override fun decodePath(params: Map<String, String>): KovenPair<A, B> = KovenPair(fieldA.decodePath(params), fieldB.decodePath(params))
}

/**
 * Creates a pair of path fields as [PathPairField].
 */
infix fun <A, B> PathField<A>.and(other: PathField<B>): PathPairField<A, B> = PathPairField(this, other)

/**
 * Creates a pair of path fields as [PathPairField], optimizing for [Empty] on the left.
 */
@JvmName("andEmptyPathFieldLeft")
infix fun <B> Empty.and(other: PathField<B>): PathField<B> = other

/**
 * Creates a pair of path fields as [PathPairField], optimizing for [Empty] on the right.
 */
@JvmName("andEmptyPathFieldRight")
infix fun <A> PathField<A>.and(other: Empty): PathField<A> = this
