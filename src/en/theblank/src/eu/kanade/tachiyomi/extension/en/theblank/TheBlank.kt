package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.multisrc.pam.CheckBoxGroup
import eu.kanade.tachiyomi.multisrc.pam.Pam
import eu.kanade.tachiyomi.multisrc.pam.SortFilter
import eu.kanade.tachiyomi.multisrc.pam.TriStateGroupFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source

@Source
abstract class TheBlank : Pam() {

    override val popularFilters = FilterList(SortFilter("Sort", sortValues, Filter.Sort.Selection(3, false)))
    override val latestFilters = FilterList(SortFilter("Sort", sortValues, Filter.Sort.Selection(2, false)))

    override fun getFilterList() = FilterList(
        Filter.Header("Text search ignores filters!"),
        Filter.Separator(),
        SortFilter("Sort", sortValues),
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    private class GenreFilter : TriStateGroupFilter("Genres", genres)
    private class TypeFilter : TriStateGroupFilter("Types", type)
    private class StatusFilter : CheckBoxGroup("Status", status)
}

private val sortValues = listOf(
    "New Series" to "date",
    "Trending" to "trending",
    "Recently Updated" to "recently",
    "Most Views" to "views",
    "A-Z" to "alphabetical",
)

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

private val status = listOf(
    "Ongoing" to "ongoing",
    "Finished" to "finished",
    "Dropped" to "dropped",
    "On Hold" to "onhold",
    "Upcoming" to "upcoming",
)
