package eu.kanade.tachiyomi.multisrc.zmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class ProjectFilter :
    UriPartFilter(
        "Filter Project",
        arrayOf(
            Pair("Show all manga", ""),
            Pair("Show only project manga", "project-filter-on"),
        ),
    )

class AuthorFilter : Filter.Text("Author")

class YearFilter : Filter.Text("Year")

class StatusFilter : Filter.TriState("Completed")

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "Doujin"),
        ),
    )

class OrderByFilter :
    UriPartFilter(
        "Order By",
        arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
        ),
    )

class Tag(val id: String, name: String) : Filter.CheckBox(name)

class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)

internal fun getGenreList() = listOf(
    Tag("4-koma", "4-Koma"),
    Tag("4-koma-comedy", "4-Koma Comedy"),
    Tag("action", "Action"),
    Tag("adult", "Adult"),
    Tag("adventure", "Adventure"),
    Tag("comedy", "Comedy"),
    Tag("demons", "Demons"),
    Tag("drama", "Drama"),
    Tag("ecchi", "Ecchi"),
    Tag("fantasy", "Fantasy"),
    Tag("game", "Game"),
    Tag("gender-bender", "Gender bender"),
    Tag("gore", "Gore"),
    Tag("harem", "Harem"),
    Tag("historical", "Historical"),
    Tag("horror", "Horror"),
    Tag("isekai", "Isekai"),
    Tag("josei", "Josei"),
    Tag("loli", "Loli"),
    Tag("magic", "Magic"),
    Tag("manga", "Manga"),
    Tag("manhua", "Manhua"),
    Tag("manhwa", "Manhwa"),
    Tag("martial-arts", "Martial Arts"),
    Tag("mature", "Mature"),
    Tag("mecha", "Mecha"),
    Tag("military", "Military"),
    Tag("monster-girls", "Monster Girls"),
    Tag("music", "Music"),
    Tag("mystery", "Mystery"),
    Tag("one-shot", "One Shot"),
    Tag("parody", "Parody"),
    Tag("police", "Police"),
    Tag("psychological", "Psychological"),
    Tag("romance", "Romance"),
    Tag("school", "School"),
    Tag("school-life", "School Life"),
    Tag("sci-fi", "Sci-Fi"),
    Tag("socks", "Socks"),
    Tag("seinen", "Seinen"),
    Tag("shoujo", "Shoujo"),
    Tag("shoujo-ai", "Shoujo Ai"),
    Tag("shounen", "Shounen"),
    Tag("shounen-ai", "Shounen Ai"),
    Tag("slice-of-life", "Slice of Life"),
    Tag("smut", "Smut"),
    Tag("sports", "Sports"),
    Tag("super-power", "Super Power"),
    Tag("supernatural", "Supernatural"),
    Tag("survival", "Survival"),
    Tag("thriller", "Thriller"),
    Tag("tragedy", "Tragedy"),
    Tag("vampire", "Vampire"),
    Tag("webtoons", "Webtoons"),
    Tag("yuri", "Yuri"),
)
