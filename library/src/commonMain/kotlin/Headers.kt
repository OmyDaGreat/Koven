package xyz.malefic.spyder

/**
 * A wrapper for HTTP headers that maintains insertion order and supports multiple values.
 * Header fields are treated case-insensitively.
 */
class Headers private constructor(
    private val data: Map<String, List<String>>,
) : Map<String, List<String>> by data,
    HeaderProvider {
    constructor(map: Map<String, List<String>> = emptyMap()) :
        this(data = map.entries.groupBy({ it.key.lowercase() }, { it.value }).mapValues { (_, values) -> values.flatten() })

    override fun containsKey(key: String): Boolean = data.containsKey(key.lowercase())

    override operator fun get(key: String): List<String>? = data[key.lowercase()]

    override fun Builder.provide() {
        forEach { (k, v) -> v.forEach { append(k, it) } }
    }

    /**
     * Gets all values for a given [HeaderField].
     */
    operator fun <T> get(field: HeaderField<T>): List<String>? = get(field.field)

    /**
     * Gets the first value for a given [HeaderField].
     *
     * @param field The [HeaderField].
     */
    fun <T> getFirst(field: HeaderField<T>): String? = get(field)?.firstOrNull()

    /**
     * Gets the first value for a given header field.
     *
     * @param field The field name.
     */
    fun getFirst(field: String): String? = get(field)?.firstOrNull()

    companion object {
        fun build(block: Builder.() -> Unit): Headers = Builder().apply(block).build()

        /**
         * Converts a collection of pairs into [Headers].
         */
        fun fromPairs(pairs: Iterable<Pair<String, String?>>): Headers =
            build {
                pairs.forEach { (k, v) -> v?.let { append(k, v) } }
            }
    }

    class Builder {
        private val map = LinkedHashMap<String, MutableList<String>>()

        fun append(
            name: String,
            value: String,
        ) = apply {
            map.getOrPut(name.lowercase()) { mutableListOf() }.add(value)
        }

        operator fun set(
            name: String,
            value: String,
        ) {
            map[name.lowercase()] = mutableListOf(value)
        }

        fun add(provider: HeaderProvider) =
            apply {
                with(provider) { provide() }
            }

        fun build(): Headers = Headers(map = map.mapValues { it.value.toList() })
    }
}

/**
 * Interface for anything that can contribute headers to a [Headers.Builder].
 */
interface HeaderProvider {
    fun Headers.Builder.provide()
}

/**
 * Interface for header fields.
 */
interface HeaderField<out T> {
    val field: String

    /**
     * Decodes the header from [Headers].
     */
    fun decode(headers: Headers): T?

    companion object {
        operator fun invoke(name: String): HeaderField<String> =
            object : HeaderField<String> {
                override val field: String = name

                override fun decode(headers: Headers): String? = headers.getFirst(name)
            }
    }
}

/**
 * Represents "No Headers" for contracts like [HealthContract] and [PingContract].
 */
object NoHeaders : HeaderProvider {
    override fun Headers.Builder.provide() {}
}

/**
 * Interface for header implementations. The companion object of this interface is used to create a [HeaderField]. You can check out an example with [BearerAuth].
 */
interface Header : HeaderProvider {
    val field: String
    val values: List<String>

    override fun Headers.Builder.provide() {
        values.forEach { append(field, it) }
    }
}

/**
 * An example header for bearer authentication.
 */
class BearerAuth(
    val token: String,
) : Header {
    override val field: String = Companion.field
    override val values: List<String> = listOf("Bearer $token")

    companion object : HeaderField<BearerAuth> {
        override val field: String = "Authorization"

        /**
         * Decodes the Authorization header from [Headers] into a [BearerAuth].
         *
         * @param headers The [Headers] to decode.
         *
         * @return The decoded [BearerAuth] or null if the header is missing or invalid.
         */
        override fun decode(headers: Headers): BearerAuth? =
            headers
                .getFirst(field)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.removePrefix("Bearer ")
                ?.let { BearerAuth(it) }
    }
}
