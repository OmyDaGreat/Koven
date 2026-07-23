package xyz.malefic.koven.feature.pagination

import xyz.malefic.koven.core.field.QueryProvider

/**
 * An interface for a pagination contract, providing information necessary for manual pagination.
 */
interface Pagination : QueryProvider {
    /**
     * The current page.
     */
    val page: Int

    /**
     * The number of items per page.
     */
    val limit: Int

    /**
     * The offset for the current page.
     */
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
