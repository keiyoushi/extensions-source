package eu.kanade.tachiyomi.extension.en.kodansha

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class StatusFilter : Filter.Select<String>("Status", STATUSES.map { it.first }.toTypedArray()) {
    val value get() = STATUSES[state].second
}

class Tag(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<Tag>("Genres", TAGS.map { Tag(it.first, it.second) })

private val SORTS = listOf(
    "Popular" to "popular",
    "Recent Series" to "recent_series",
    "Alphabetical" to "alphabetical",
)

private val STATUSES = listOf(
    "All" to null,
    "Ongoing" to "incomplete",
    "Completed" to "complete",
)

private val TAGS = listOf(
    "Action & Adventure" to "action-and-adventure",
    "Animals" to "animals",
    "Arts & Entertainment" to "arts-and-entertainment",
    "Biography" to "biography",
    "BL/Yaoi" to "blyaoi",
    "Comedy" to "comedy",
    "Crafts" to "crafts",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Fiction & Literature" to "fiction-and-literature",
    "Food" to "food",
    "Games" to "games",
    "GL/Yuri" to "glyuri",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "LGBTQ" to "lgbtq",
    "Made into Anime" to "made-into-anime",
    "Martial Arts" to "martial-arts",
    "Movie/TV Tie-in" to "movietv-tie-in",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Science-Fiction" to "science-fiction",
    "Slice of Life" to "slice-of-life",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Videogame Tie-in" to "videogame-tie-in",
)
