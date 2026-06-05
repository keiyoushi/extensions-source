package eu.kanade.tachiyomi.extension.en.kunmangaonline

import eu.kanade.tachiyomi.source.model.Filter

internal class AuthorFilter : Filter.Text("Author")
internal class ArtistFilter : Filter.Text("Artist")
internal class YearFilter : Filter.Text("Release Year")

internal class OperatorFilter : Filter.Select<String>("Genre Operator", arrayOf("OR", "AND")) {
    fun selectedValue() = if (state == 0) "" else "1"
}

internal class AdultFilter : Filter.Select<String>("Adult Content", arrayOf("All", "Safe (18-)", "Adult (18+)")) {
    fun selectedValue() = when (state) {
        1 -> "0"
        2 -> "1"
        else -> ""
    }
}

internal class KMoOrderByFilter : Filter.Select<String>("Order By", arrayOf("Relevance", "Latest", "A-Z", "Rating", "Trending", "Most Views", "New Manga")) {
    fun selectedValue() = when (state) {
        1 -> "latest"
        2 -> "alphabet"
        3 -> "rating"
        4 -> "trending"
        5 -> "views"
        6 -> "new-manga"
        else -> ""
    }
}

internal class Status(name: String, val value: String) : Filter.CheckBox(name)
internal class StatusListFilter(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

internal fun getStatusList() = listOf(
    Status("On-Going", "ongoing"),
    Status("Completed", "completed"),
    Status("On-Hold", "on-hold"),
    Status("Dropped", "drop"),
)

internal class Genre(name: String, val value: String) : Filter.CheckBox(name)
internal class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

internal fun getGenreList() = listOf(
    Genre("Adaptation", "adaptation"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Cooking", "cooking"),
    Genre("Demons", "demons"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Full Color", "full-color"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Long strip", "long-strip"),
    Genre("Magic", "magic"),
    Genre("Manga", "manga"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Monster", "monster"),
    Genre("Mystery", "mystery"),
    Genre("Office Workers", "office-workers"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("School Life", "school-life"),
    Genre("Sci fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("Thriller", "thriller"),
    Genre("Time Travel", "time-travel"),
    Genre("Tragedy", "tragedy"),
    Genre("Villainess", "villainess"),
    Genre("Web comic", "web-comic"),
    Genre("Webtoons", "webtoons"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
    Genre("Zombies", "zombies"),
)
