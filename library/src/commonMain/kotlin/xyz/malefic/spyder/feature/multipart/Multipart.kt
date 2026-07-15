package xyz.malefic.spyder.feature.multipart

/**
 * A representation of a multipart form request.
 *
 * @property fields A map of form fields and their values.
 * @property files A map of form files and their data.
 */
data class Multipart(
    val fields: Map<String, String> = emptyMap(),
    val files: Map<String, File> = emptyMap(),
) {
    /**
     * A representation of a file in a multipart form request.
     *
     * @property name The name of the file.
     * @property contentType The content type of the file.
     * @property bytes The content of the file.
     */
    data class File(
        val name: String,
        val contentType: String?,
        val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is File) return false

            if (name != other.name) return false
            if (contentType != other.contentType) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}
