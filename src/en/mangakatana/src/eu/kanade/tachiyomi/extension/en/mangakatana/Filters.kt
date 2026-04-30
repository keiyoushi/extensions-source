package eu.kanade.tachiyomi.extension.en.mangakatana

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter :
    UriPartFilter(
        "Text search by",
        arrayOf(
            Pair("Title", "book_name"),
            Pair("Author", "author"),
        ),
    )

class Genre(val id: String, name: String) : Filter.TriState(name)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class GenreInclusionMode :
    UriPartFilter(
        "Genre inclusion mode",
        arrayOf(
            Pair("And", "and"),
            Pair("Or", "or"),
        ),
    )

class ChaptersFilter : Filter.Text("Minimum Chapters")

class SortFilter :
    UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Latest update", "latest"),
            Pair("New manga", "new"),
            Pair("A-Z", "az"),
            Pair("Number of chapters", "numc"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Cancelled", "0"),
            Pair("Ongoing", "1"),
            Pair("Completed", "2"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

val genres = listOf(
    Genre("4-koma", "4 koma"),
    Genre("action", "Action"),
    Genre("adult", "Adult"),
    Genre("adventure", "Adventure"),
    Genre("artbook", "Artbook"),
    Genre("award-winning", "Award winning"),
    Genre("comedy", "Comedy"),
    Genre("cooking", "Cooking"),
    Genre("doujinshi", "Doujinshi"),
    Genre("drama", "Drama"),
    Genre("ecchi", "Ecchi"),
    Genre("erotica", "Erotica"),
    Genre("fantasy", "Fantasy"),
    Genre("gender-bender", "Gender Bender"),
    Genre("gore", "Gore"),
    Genre("harem", "Harem"),
    Genre("historical", "Historical"),
    Genre("horror", "Horror"),
    Genre("isekai", "Isekai"),
    Genre("josei", "Josei"),
    Genre("loli", "Loli"),
    Genre("manhua", "Manhua"),
    Genre("manhwa", "Manhwa"),
    Genre("martial-arts", "Martial Arts"),
    Genre("mecha", "Mecha"),
    Genre("medical", "Medical"),
    Genre("music", "Music"),
    Genre("mystery", "Mystery"),
    Genre("one-shot", "One shot"),
    Genre("overpowered-mc", "Overpowered MC"),
    Genre("psychological", "Psychological"),
    Genre("reincarnation", "Reincarnation"),
    Genre("romance", "Romance"),
    Genre("school-life", "School Life"),
    Genre("sci-fi", "Sci-fi"),
    Genre("seinen", "Seinen"),
    Genre("sexual-violence", "Sexual violence"),
    Genre("shota", "Shota"),
    Genre("shoujo", "Shoujo"),
    Genre("shoujo-ai", "Shoujo Ai"),
    Genre("shounen", "Shounen"),
    Genre("shounen-ai", "Shounen Ai"),
    Genre("slice-of-life", "Slice of Life"),
    Genre("sports", "Sports"),
    Genre("super-power", "Super power"),
    Genre("supernatural", "Supernatural"),
    Genre("survival", "Survival"),
    Genre("time-travel", "Time Travel"),
    Genre("tragedy", "Tragedy"),
    Genre("webtoon", "Webtoon"),
    Genre("yaoi", "Yaoi"),
    Genre("yuri", "Yuri"),
)
