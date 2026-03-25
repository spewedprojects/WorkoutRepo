package com.gratus.workoutrepo.utils

object FilterEngine {

    /**
     * A generic filter engine that can filter any list of items based on a search query and a category/type.
     *
     * @param items The list of items to filter.
     * @param searchQuery The text to search for.
     * @param searchableTextSelector A lambda that provides a list of strings from the item to search against.
     * @param typeFilter The selected type to filter by (or null if no type filter is active).
     * @param typeSelector A lambda that provides the category/type of the item.
     * @return The filtered list of items.
     */
    fun <T> filterItems(
        items: List<T>,
        searchQuery: String,
        searchableTextSelector: (T) -> List<String?>,
        typeFilter: String?,
        typeSelector: (T) -> String?
    ): List<T> {
        var filtered = items

        // 1. Text Search Filter
        if (searchQuery.trim().isNotEmpty()) {
            val query = searchQuery.trim()
            filtered = filtered.filter { item ->
                searchableTextSelector(item).any { text ->
                    text?.contains(query, ignoreCase = true) == true
                }
            }
        }

        // 2. Type Filter
        if (!typeFilter.isNullOrBlank()) {
            filtered = filtered.filter { item ->
                typeSelector(item).equals(typeFilter, ignoreCase = true)
            }
        }

        return filtered
    }
}
