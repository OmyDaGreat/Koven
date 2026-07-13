package xyz.malefic.spyder

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/**
 * An interface for a pagination contract, providing information necessary for manual pagination.
 */
interface Pagination : QueryProvider {
    val page: Int
    val limit: Int
    val offset: Int

    /**
     * If this is set, the framework knows the list is already filtered and won't attempt to slice it in memory.
     */
    var totalItems: Long?

    override fun provideQuery(): Map<String, List<String>> =
        mapOf(
            "page" to listOf(page.toString()),
            "limit" to listOf(limit.toString()),
        )
}

/**
 * A response containing a paginated list of items.
 */
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
) {
    /**
     * Maps the items of this response to a new type [R].
     */
    fun <R> map(transform: (T) -> R): PaginatedResponse<R> =
        PaginatedResponse(
            items = items.map(transform),
            page = page,
            limit = limit,
            totalItems = totalItems,
            totalPages = totalPages,
            hasNext = hasNext,
            hasPrevious = hasPrevious,
        )

    companion object {
        /**
         * Creates a [PaginatedResponse] by calculating metadata based on a 1-indexed page.
         */
        fun <T> create(
            items: List<T>,
            page: Int,
            limit: Int,
            totalItems: Long,
        ): PaginatedResponse<T> {
            val totalPages = if (limit > 0) ceil(totalItems.toDouble() / limit).toInt() else 0
            return PaginatedResponse(
                items = items,
                page = page,
                limit = limit,
                totalItems = totalItems,
                totalPages = totalPages,
                hasNext = page < totalPages,
                hasPrevious = page > 1,
            )
        }
    }
}
