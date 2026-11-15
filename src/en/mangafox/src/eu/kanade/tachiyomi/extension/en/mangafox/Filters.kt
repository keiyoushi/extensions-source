package eu.kanade.tachiyomi.extension.en.mangafox

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    name: String,
    val query: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

open class TextSearchMethodFilter(name: String, query: String) : UriPartFilter(name, query, arrayOf(Pair("contain", "cw"), Pair("begin", "bw"), Pair("end", "ew")))

open class TextSearchFilter(name: String, val query: String) : Filter.Text(name)

open class FilterWithMethodAndText(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)

class NameFilter : TextSearchFilter("Name", "name")

class EntryTypeFilter :
    UriPartFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Any", "0"),
            Pair("Japanese Manga", "1"),
            Pair("Korean Manhwa", "2"),
            Pair("Chinese Manhua", "3"),
            Pair("European Manga", "4"),
            Pair("American Manga", "5"),
            Pair("HongKong Manga", "6"),
            Pair("Other Manga", "7"),
        ),
    )

class AuthorMethodFilter : TextSearchMethodFilter("Method", "author_method")

class AuthorTextFilter : TextSearchFilter("Author", "author")

class AuthorFilter : FilterWithMethodAndText("Author", listOf(AuthorMethodFilter(), AuthorTextFilter()))

class ArtistMethodFilter : TextSearchMethodFilter("Method", "artist_method")

class ArtistTextFilter : TextSearchFilter("Artist", "artist")

class ArtistFilter : FilterWithMethodAndText("Artist", listOf(ArtistMethodFilter(), ArtistTextFilter()))

class RatingMethodFilter :
    UriPartFilter(
        "Method",
        "rating_method",
        arrayOf(
            Pair("is", "eq"),
            Pair("less than", "lt"),
            Pair("more than", "gt"),
        ),
    )

class RatingValueFilter :
    UriPartFilter(
        "Rating",
        "rating",
        arrayOf(
            Pair("any star", ""),
            Pair("no star", "0"),
            Pair("1 star", "1"),
            Pair("2 stars", "2"),
            Pair("3 stars", "3"),
            Pair("4 stars", "4"),
            Pair("5 stars", "5"),
        ),
    )

class RatingFilter : Filter.Group<UriPartFilter>("Rating", listOf(RatingMethodFilter(), RatingValueFilter()))

class YearMethodFilter :
    UriPartFilter(
        "Method",
        "released_method",
        arrayOf(
            Pair("on", "eq"),
            Pair("before", "lt"),
            Pair("after", "gt"),
        ),
    )

class YearTextFilter : TextSearchFilter("Release year", "released")

class YearFilter : FilterWithMethodAndText("Release year", listOf(YearMethodFilter(), YearTextFilter()))

class CompletedFilter :
    UriPartFilter(
        "Completed Series",
        "st",
        arrayOf(
            Pair("Either", "0"),
            Pair("Yes", "2"),
            Pair("No", "1"),
        ),
    )

class Genre(name: String, val id: Int) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

fun getGenreList() = listOf(
    Genre("Action", 1),
    Genre("Adventure", 2),
    Genre("Comedy", 3),
    Genre("Drama", 4),
    Genre("Fantasy", 5),
    Genre("Martial Arts", 6),
    Genre("Shounen", 7),
    Genre("Horror", 8),
    Genre("Supernatural", 9),
    Genre("Harem", 10),
    Genre("Psychological", 11),
    Genre("Romance", 12),
    Genre("School Life", 13),
    Genre("Shoujo", 14),
    Genre("Mystery", 15),
    Genre("Sci-fi", 16),
    Genre("Seinen", 17),
    Genre("Tragedy", 18),
    Genre("Ecchi", 19),
    Genre("Sports", 20),
    Genre("Slice of Life", 21),
    Genre("Mature", 22),
    Genre("Shoujo Ai", 23),
    Genre("Webtoons", 24),
    Genre("Doujinshi", 25),
    Genre("One Shot", 26),
    Genre("Smut", 27),
    Genre("Yaoi", 28),
    Genre("Josei", 29),
    Genre("Historical", 30),
    Genre("Shounen Ai", 31),
    Genre("Gender Bender", 32),
    Genre("Adult", 33),
    Genre("Yuri", 34),
    Genre("Mecha", 35),
    Genre("Lolicon", 36),
    Genre("Shotacon", 37),
)
