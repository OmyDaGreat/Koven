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
        this(data = map.mapKeys { it.key.lowercase() })

    override fun containsKey(key: String): Boolean = data.containsKey(key.lowercase())

    override operator fun get(key: String): List<String>? = data[key.lowercase()]

    override fun Builder.provide() {
        forEach { (k, v) -> v.forEach { append(k, it) } }
    }

    /**
     * Gets all values for a given [HeaderField].
     */
    operator fun get(field: HeaderField): List<String>? = get(field.field)

    /**
     * Gets the first value for a given [HeaderField].
     *
     * @param field The [HeaderField].
     */
    fun getFirst(field: HeaderField): String? = get(field)?.firstOrNull()

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
            map.getOrPut(name) { mutableListOf() }.add(value)
        }

        operator fun set(
            name: String,
            value: String,
        ) {
            map[name] = mutableListOf(value)
        }

        fun add(provider: HeaderProvider) =
            apply {
                with(provider) { provide() }
            }

        internal fun build(): Headers = Headers(map = map.mapValues { it.value.toList() })
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
interface HeaderField {
    val field: String

    companion object {
        operator fun invoke(name: String): HeaderField =
            object : HeaderField {
                override val field: String = name
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
 * Interface for header implementations.
 */
interface Header :
    HeaderField,
    HeaderProvider {
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

    companion object : HeaderField {
        override val field: String = "Authorization"
    }
}
