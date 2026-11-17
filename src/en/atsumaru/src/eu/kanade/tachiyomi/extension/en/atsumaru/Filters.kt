package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal class TriStateOption(name: String, val id: String, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

internal class CheckBoxOption(name: String, val id: String) : Filter.CheckBox(name)

internal class GenreFilter(genres: List<TriStateOption>) : Filter.Group<TriStateOption>("Genres", genres) {
    fun ids(triState: Int) = state.filter { it.state == triState }.map { it.id }
}

internal class TagLetterFilter(letter: String, tags: List<TriStateOption>) : Filter.Group<TriStateOption>(letter, tags)

internal class TagFilters(letters: List<TagLetterFilter>) : Filter.Group<TagLetterFilter>("Tags", letters) {
    fun ids(triState: Int) = state.flatMap { letter -> letter.state.filter { it.state == triState }.map { it.id } }
}

internal class TypeFilter(types: List<CheckBoxOption>) : Filter.Group<CheckBoxOption>("Manga Type", types) {
    val selectedIds get() = state.filter { it.state }.map { it.id }
}

internal class StatusFilter(statuses: List<CheckBoxOption>) : Filter.Group<CheckBoxOption>("Publishing Status", statuses) {
    val selectedIds get() = state.filter { it.state }.map { it.id }
}

internal class YearFilter : Filter.Text("Years (e.g., 2019, 2020)")

internal class MinChaptersFilter : Filter.Text("Minimum Chapters")

internal class MaxChaptersFilter : Filter.Text("Maximum Chapters")

internal class SortFilter : Filter.Select<SortOption>("Sort By", SortOption.ALL.toTypedArray()) {
    val selected get() = values[state]
}

internal class Timeframe(private val title: String, val value: String) {
    override fun toString() = title
}

internal class TimeframeFilter :
    Filter.Select<Timeframe>(
        "Timeframe (Popularity, Most Bookmarked, Most Talked About)",
        arrayOf(Timeframe("All time", "all"), Timeframe("Daily", "daily"), Timeframe("Weekly", "weekly"), Timeframe("Monthly", "monthly")),
    ) {
    val selected get() = values[state].value
}

internal class SortOption(
    private val title: String,
    private val typesenseSort: String? = null,
    private val home2Path: String? = null,
    val supportsTimeframe: Boolean = false,
) {
    override fun toString() = title

    // The site's explore page reads these from its search index, unless the timeframe isn't "All time"
    fun home2Path(timeframe: String) = home2Path?.takeIf { typesenseSort == null || (supportsTimeframe && timeframe != "all") }

    fun typesenseSort(seed: Int) = typesenseSort ?: randomSort(seed)

    // The site's seeded shuffle over its two random index fields
    private fun randomSort(seed: Int): String {
        val (first, second) = if (seed % 2 == 0) "shuffleA" to "shuffleB" else "shuffleB" to "shuffleA"
        return if ((seed ushr 1) and 1 == 1) {
            "_eval($first:<=$seed):desc,$first:desc,$second:desc"
        } else {
            "_eval($first:>=$seed):desc,$first:asc,$second:asc"
        }
    }

    companion object {
        val POPULARITY = SortOption("Popularity", "views:desc", "popular", supportsTimeframe = true)
        val TOP_RATED = SortOption("Top Rated", "mbRating:desc")
        val RANDOM = SortOption("Random")

        val ALL = listOf(
            POPULARITY,
            SortOption("Trending", "trending:desc"),
            SortOption("Recently Added", "dateAdded:desc"),
            SortOption("Release Date", "releaseDate:desc"),
            TOP_RATED,
            SortOption("Title", "title:asc"),
            SortOption("Hot Updates", home2Path = "hotUpdates"),
            SortOption("Recently Updated", home2Path = "recentlyUpdated"),
            SortOption("Rising", home2Path = "rising"),
            SortOption("Hot Arrivals", home2Path = "hotArrivals"),
            SortOption("Most Bookmarked", home2Path = "mostBookmarked", supportsTimeframe = true),
            SortOption("Most Talked About", home2Path = "mostTalkedAbout", supportsTimeframe = true),
            SortOption("Binge-Worthy", home2Path = "bingeWorthy"),
            SortOption("Most Polarizing", home2Path = "mostPolarizing"),
            SortOption("Hidden Gems", home2Path = "hiddenGems"),
            RANDOM,
        )
    }
}

internal class AdultFilter : Filter.CheckBox("Show Adult Content")

internal class OfficialFilter : Filter.CheckBox("Only Official Translations", false)
