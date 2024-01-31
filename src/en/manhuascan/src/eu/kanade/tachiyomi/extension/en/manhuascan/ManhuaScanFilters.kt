package eu.kanade.tachiyomi.extension.en.manhuascan

import eu.kanade.tachiyomi.source.model.Filter

inline fun <reified T> List<*>.firstInstanceOrNull() = firstOrNull { it is T } as? T

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class Genre(val id: String, name: String) : Filter.TriState(name)

class GenreFilter : Filter.Group<Genre>(
    "Genres",
    listOf(
        Genre("action", "Action"),
        Genre("adaptation", "Adaptation"),
        Genre("adult", "Adult"),
        Genre("adventure", "Adventure"),
        Genre("animal", "Animal"),
        Genre("anthology", "Anthology"),
        Genre("cartoon", "Cartoon"),
        Genre("comedy", "Comedy"),
        Genre("comic", "Comic"),
        Genre("cooking", "Cooking"),
        Genre("demons", "Demons"),
        Genre("doujinshi", "Doujinshi"),
        Genre("drama", "Drama"),
        Genre("ecchi", "Ecchi"),
        Genre("fantasy", "Fantasy"),
        Genre("full-color", "Full Color"),
        Genre("game", "Game"),
        Genre("gender-bender", "Gender bender"),
        Genre("ghosts", "Ghosts"),
        Genre("harem", "Harem"),
        Genre("historical", "Historical"),
        Genre("horror", "Horror"),
        Genre("isekai", "Isekai"),
        Genre("josei", "Josei"),
        Genre("long-strip", "Long strip"),
        Genre("mafia", "Mafia"),
        Genre("magic", "Magic"),
        Genre("manga", "Manga"),
        Genre("manhua", "Manhua"),
        Genre("manhwa", "Manhwa"),
        Genre("martial-arts", "Martial arts"),
        Genre("mature", "Mature"),
        Genre("mecha", "Mecha"),
        Genre("medical", "Medical"),
        Genre("military", "Military"),
        Genre("monster", "Monster"),
        Genre("monster-girls", "Monster girls"),
        Genre("monsters", "Monsters"),
        Genre("music", "Music"),
        Genre("mystery", "Mystery"),
        Genre("office", "Office"),
        Genre("office-workers", "Office workers"),
        Genre("one-shot", "One shot"),
        Genre("police", "Police"),
        Genre("psychological", "Psychological"),
        Genre("reincarnation", "Reincarnation"),
        Genre("romance", "Romance"),
        Genre("school-life", "School life"),
        Genre("sci-fi", "Sci fi"),
        Genre("science-fiction", "Science fiction"),
        Genre("seinen", "Seinen"),
        Genre("shoujo", "Shoujo"),
        Genre("shoujo-ai", "Shoujo ai"),
        Genre("shounen", "Shounen"),
        Genre("shounen-ai", "Shounen ai"),
        Genre("slice-of-life", "Slice of life"),
        Genre("smut", "Smut"),
        Genre("soft-yaoi", "Soft Yaoi"),
        Genre("sports", "Sports"),
        Genre("super-power", "Super Power"),
        Genre("superhero", "Superhero"),
        Genre("supernatural", "Supernatural"),
        Genre("thriller", "Thriller"),
        Genre("time-travel", "Time travel"),
        Genre("tragedy", "Tragedy"),
        Genre("vampire", "Vampire"),
        Genre("vampires", "Vampires"),
        Genre("video-games", "Video games"),
        Genre("villainess", "Villainess"),
        Genre("web-comic", "Web comic"),
        Genre("webtoons", "Webtoons"),
        Genre("yaoi", "Yaoi"),
        Genre("yuri", "Yuri"),
        Genre("zombies", "Zombies"),
    ),
) {
    val included: List<String>?
        get() = state.filter { it.isIncluded() }.map { it.id }.takeUnless { it.isEmpty() }

    val excluded: List<String>?
        get() = state.filter { it.isExcluded() }.map { it.id }.takeUnless { it.isEmpty() }
}

class GenreInclusionFilter : UriPartFilter(
    "Genre Inclusion Mode",
    arrayOf(
        Pair("AND (All Selected Genres)", "and"),
        Pair("OR (Any Selected Genres)", "or"),
    ),
)

class StatusFilter : UriPartFilter(
    "Status",
    arrayOf(
        Pair("All", "all"),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed"),
    ),
)

class OrderByFilter : UriPartFilter(
    "Order By",
    arrayOf(
        Pair("Views", "views"),
        Pair("Updated", "updated_at"),
        Pair("Created", "created_at"),
        Pair("Name A-Z", "name"),
        Pair("Number of Chapters", "total_chapters"),
        Pair("Rating", "rating"),
    ),
)

class AuthorFilter : Filter.Text("Author name")
