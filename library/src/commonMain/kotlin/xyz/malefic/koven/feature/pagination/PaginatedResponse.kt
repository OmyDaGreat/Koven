package xyz.malefic.koven.feature.pagination

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/**
 * A response containing a paginated list of items.
 *
 * @param items The list of items.
 * @param page The current page.
 * @param limit The number of items per page.
 * @param totalItems The total number of items.
 * @param totalPages The total number of pages.
 * @param hasNext Whether there is a next page.
 * @param hasPrevious Whether there is a previous page.
 *
 * @param T The type of the items in the list.
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
