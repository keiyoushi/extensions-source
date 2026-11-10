package eu.kanade.tachiyomi.extension.en.eahentai

import eu.kanade.tachiyomi.source.model.Filter

const val SORT_LATEST = "date_desc"
const val SORT_ALL_TIME = "views_alltime"

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray(), SORTS.lastIndex) {
    val value get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Latest" to SORT_LATEST,
            "Popular today" to "views_daily",
            "Popular this week" to "views_weekly",
            "Popular this month" to "views_monthly",
            "Popular all time" to SORT_ALL_TIME,
        )
    }
}

class SearchTypeFilter : Filter.Select<String>("Search in", TYPES.map { it.first }.toTypedArray()) {
    val value get() = TYPES[state].second

    companion object {
        private val TYPES = listOf(
            "Everything" to "gallery",
            "Artists" to "artist",
            "Characters" to "character",
            "Parodies" to "series",
            "Categories" to "albumType",
        )
    }
}
