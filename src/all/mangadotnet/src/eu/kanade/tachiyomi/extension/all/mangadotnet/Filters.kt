package eu.kanade.tachiyomi.extension.all.mangadotnet

import eu.kanade.tachiyomi.source.model.Filter

class BrowseFilter :
    Filter.Select<String>(
        name = "Browse",
        values = browseOptions.map { it.first }.toTypedArray(),
    ) {
    val selected get() = browseOptions[state].second
}

private val browseOptions = listOf(
    "None" to "",
    "Most Tracked" to "most-tracked",
    "Top Rated" to "top-rated",
    "Latest Updates" to "latest-updates",
    "Recently Added" to "recently-added",
    "Bookmarks" to "bookmarks",
)

class SortFilter :
    Filter.Sort(
        name = "Sort",
        values = sortOrders.map { it.first }.toTypedArray(),
        state = Selection(0, false),
    ) {
    val sort get() = sortOrders[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false
}

private val sortOrders = listOf(
    "Relevance" to "",
    "Latest Update" to "latest",
    "Alphabetical" to "alphabetical",
    "Total Chapters" to "chapters",
    "Most Viewed" to "views",
    "Most Tracked" to "tracked",
    "Top Rated" to "rating",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = status.map { it.first }.toTypedArray(),
    ) {
    val selected get() = status[state].second
}

private val status = listOf(
    "Any Status" to null,
    "Ongoing" to "Ongoing",
    "Completed" to "Completed",
    "Hiatus" to "Hiatus",
)

class VolumesFilter :
    Filter.Select<String>(
        name = "Volumes",
        values = volumeOptions.map { it.first }.toTypedArray(),
    ) {
    val selected get() = volumeOptions[state].second
}

private val volumeOptions = listOf(
    "Any" to "",
    "Has Volumes" to "1",
    "No Volumes" to "0",
)

class ScanlatorFilter :
    Filter.Select<String>(
        name = "Scanlator Group",
        values = scanlatorOptions.map { it.first }.toTypedArray(),
    ) {
    val selected get() = scanlatorOptions[state].second
}

private val scanlatorOptions = listOf(
    "Any" to "",
    "Scanlator Group" to "1",
    "No Scanlator Group" to "0",
)

class LibraryFilter :
    Filter.Select<String>(
        name = "Library",
        values = libraryOptions.map { it.first }.toTypedArray(),
    ) {
    val selected get() = libraryOptions[state].second
}

private val libraryOptions = listOf(
    "Any" to "",
    "In Library" to "in",
    "Not In Library" to "out",
)

class MinRatingFilter :
    Filter.Select<String>(
        name = "Minimum Rating",
        values = ratingOptions.map { it.first }.toTypedArray(),
    ) {
    val selected get() = ratingOptions[state].second
}

private val ratingOptions = listOf(
    "Any" to "",
    "★ 1+" to "1",
    "★ 2+" to "2",
    "★ 3+" to "3",
    "★ 4+" to "4",
    "★ 5+" to "5",
    "★ 6+" to "6",
    "★ 7+" to "7",
    "★ 8+" to "8",
    "★ 9+" to "9",
)

class TypeCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter :
    Filter.Group<TypeCheckBox>(
        "Types",
        types.map { TypeCheckBox(it.first, it.second) },
    ) {
    val checked get() = state.filter { it.state }.map { it.value }
}

private val types = listOf(
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
    "One Shot" to "ONESHOT",
)

class ContentRatingFilter(excluded: Set<String> = emptySet()) :
    Filter.Group<TriStateFilter>(
        "Content Rating",
        contentRatings.map { (name, value) ->
            val state = if (value in excluded) TriState.STATE_EXCLUDE else TriState.STATE_IGNORE
            TriStateFilter(name, value, state)
        },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

val contentRatings = listOf(
    "Safe" to "safe",
    "Suggestive" to "suggestive",
    "Erotica" to "erotica",
    "Pornographic" to "pornographic",
)

class TriStateFilter(name: String, val value: String = name, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

class DemographicFilter(excluded: Set<String> = emptySet()) :
    Filter.Group<TriStateFilter>(
        name = "Demographics",
        state = demographics.map { demo ->
            val state = if (demo in excluded) TriState.STATE_EXCLUDE else TriState.STATE_IGNORE
            TriStateFilter(demo, state = state)
        },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

private val demographics = listOf("Josei", "Seinen", "Shoujo", "Shounen")

class GenreFilter(genreValues: List<String>, excluded: Set<String>) :
    Filter.Group<TriStateFilter>(
        name = "Genres",
        state = genreValues.map { genre ->
            val state = if (genre in excluded) TriState.STATE_EXCLUDE else TriState.STATE_IGNORE
            TriStateFilter(genre, state = state)
        },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class TagsGroupFilter(
    state: List<TagFilter>,
) : Filter.Group<TagFilter>("Tags", state)

class TagFilter(name: String, tagValues: List<String>, excluded: Set<String> = emptySet()) :
    Filter.Group<TriStateFilter>(
        name = name,
        state = tagValues.map { tag ->
            val state = if (tag in excluded) TriState.STATE_EXCLUDE else TriState.STATE_IGNORE
            TriStateFilter(tag, state = state)
        },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class MinChaptersFilter : Filter.Text("Minimum Chapters")

class MinYearFilter : Filter.Text("Minimum Year")

class MaxYearFilter : Filter.Text("Maximum Year")

class AuthorFilter : Filter.Text("Author")

class ArtistFilter : Filter.Text("Artist")
