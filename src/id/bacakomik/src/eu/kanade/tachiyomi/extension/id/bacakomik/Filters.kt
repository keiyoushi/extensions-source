package eu.kanade.tachiyomi.extension.id.bacakomik

import eu.kanade.tachiyomi.source.model.Filter

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("Default", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        ),
    )

class SortByFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

class Genre(name: String, val id: String = name) : Filter.TriState(name)
class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

fun getGenreList() = listOf(
    Genre("4-Koma", "4-koma"),
    Genre("4-Koma. Comedy", "4-koma-comedy"),
    Genre("Action", "action"),
    Genre("Action. Adventure", "action-adventure"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Cooking", "cooking"),
    Genre("Demons", "demons"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Echi", "echi"),
    Genre("Fantasy", "fantasy"),
    Genre("Game", "game"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Gore", "gore"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Magic", "magic"),
    Genre("Manga", "manga"),
    Genre("Manhua", "manhua"),
    Genre("Manhwa", "manhwa"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Medical", "medical"),
    Genre("Military", "military"),
    Genre("Music", "music"),
    Genre("Mystery", "mystery"),
    Genre("One Shot", "one-shot"),
    Genre("Oneshot", "oneshot"),
    Genre("Parody", "parody"),
    Genre("Police", "police"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("Samurai", "samurai"),
    Genre("School", "school"),
    Genre("School Life", "school-life"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Super Power", "super-power"),
    Genre("Supernatural", "supernatural"),
    Genre("Thriller", "thriller"),
    Genre("Tragedy", "tragedy"),
    Genre("Vampire", "vampire"),
    Genre("Webtoon", "webtoon"),
    Genre("Webtoons", "webtoons"),
    Genre("Yuri", "yuri"),
)
