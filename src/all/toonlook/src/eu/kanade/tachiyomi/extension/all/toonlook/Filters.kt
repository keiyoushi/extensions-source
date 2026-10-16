package eu.kanade.tachiyomi.extension.all.toonlook

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection

internal class GenreFilter(genres: List<GenreDto>) : Filter.Select<String>("Genre", (listOf("All") + genres.map { it.name }).toTypedArray()) {
    private val slugs = listOf<String?>(null) + genres.map { it.slug }

    fun toSlug(): String? = slugs[state]
}

internal class StatusFilter : Filter.Select<String>("Status", STATUS_OPTIONS.map { it.first }.toTypedArray()) {
    fun toStatus(): String? = STATUS_OPTIONS[state].second

    companion object {
        private val STATUS_OPTIONS = listOf(
            "All Status" to null,
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Dropped" to "dropped",
        )
    }
}

internal class SortFilter(selection: Selection = POPULAR.state!!) : Filter.Sort("Sort", SORT_OPTIONS.map { it.first }.toTypedArray(), selection) {
    fun sortBy(): String = SORT_OPTIONS[state!!.index].second

    companion object {
        private val SORT_OPTIONS = listOf(
            "Latest Release" to "latest",
            "New Manhwa" to "new",
            "A-Z" to "az",
            "Rating" to "rating",
            "Bookmarks" to "bookmarks",
            "Daily Views" to "daily_top",
            "Weekly Views" to "weekly_top",
            "Monthly Views" to "monthly_top",
            "Yearly Views" to "yearly_top",
            "All-time Views" to "all_top",
        )

        val POPULAR = SortFilter(Selection(5, false))
        val LATEST = SortFilter(Selection(0, false))
    }
}
