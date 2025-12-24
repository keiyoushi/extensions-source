package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlin.collections.filter

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class SortFilter(
    selection: Selection = Selection(0, false),
) : Filter.Sort(
    name = "Sort",
    values = sortValues.map { it.second }.toTypedArray(),
    state = selection,
) {
    val sort get() = sortValues[state?.index ?: 0].second
    val ascending get() = state?.ascending ?: false

    companion object {
        val popular = FilterList(SortFilter(Selection(3, false)))
        val latest = FilterList(SortFilter(Selection(2, false)))
    }
}

private val sortValues = listOf(
    "New Series" to "date",
    "Trending" to "trending",
    "Recently Updated" to "recently",
    "Most Views" to "views",
    "A-Z" to "alphabetical",
)

class GenreFilter : TriStateGroupFilter("Genres", genres)

private val genres = listOf(
    "Action" to "action",
    "Adventure" to "adventure",
    "Ai" to "ai",
    "Animated" to "animated",
    "Anthology" to "anthology",
    "Cohabitation" to "cohabitation",
    "College" to "college",
    "Comedy" to "comedy",
    "Doujinshi" to "doujinshi",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Folklore" to "folklore",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Love triangle" to "love-triangle",
    "Martial arts" to "martial-arts",
    "Mature" to "mature",
    "Murim" to "murim",
    "Mystery" to "mystery",
    "Office workers" to "office-workers",
    "Psychological" to "psychological",
    "Robots" to "robots",
    "Romance" to "romance",
    "School life" to "school-life",
    "Sci-fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Superpower" to "superpower",
    "System" to "system",
    "Thriller" to "thriller",
    "Uncensored" to "uncensored",
    "Violence" to "violence",
    "Workplace" to "workplace",
)

class TypeFilter : TriStateGroupFilter("Types", type)

private val type = listOf(
    "Comic" to "comic",
    "Doujin" to "doujin",
    "Josei" to "josei",
    "Manga" to "manga",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Pornhwa" to "pornhwa",
    "Webtoon" to "webtoon",
)

class StatusFilter : CheckBoxGroup("Status", status)

private val status = listOf(
    "Ongoing" to "ongoing",
    "Finished" to "finished",
    "Dropped" to "dropped",
    "On Hold" to "onhold",
    "Upcoming" to "upcoming",
)
