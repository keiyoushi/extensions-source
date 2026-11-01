package eu.kanade.tachiyomi.extension.all.rawkumame

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class OrderFilter :
    UriPartFilter(
        "Order",
        arrayOf(
            "Popular" to "popular",
            "Latest update" to "date",
            "Title A-Z" to "title",
            "Rating" to "rating",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Novel" to "novel",
        ),
    )

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<Genre>("Genres", GENRES.map(::Genre)) {
    fun toUriPart() = state.filter { it.state }.joinToString(",") { it.name }
}

private val GENRES = listOf(
    "Action", "Adult", "Adventure", "Comedy", "Crime", "Drama", "Ecchi", "Fantasy",
    "Gender Bender", "Girls' Love", "Harem", "Hentai", "Historical", "Horror", "isekai",
    "Josei", "Lolicon", "magic", "Martial Arts", "Mature", "Mecha", "Mystery", "Oneshot",
    "Psychological", "Romance", "School Life", "Sci-fi", "Seinen", "Shotacon", "Shoujo",
    "Shoujo Ai", "Shounen", "Shounen Ai", "Slice of Life", "Smut", "Sports", "Supernatural",
    "Thriller", "Tragedy", "Yaoi", "Yuri",
)
