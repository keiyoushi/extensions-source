package eu.kanade.tachiyomi.extension.en.hentaireadio

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun getFilters() = FilterList(
    StatusFilter(),
    SortFilter(),
    Filter.Separator(),
    GenreFilter(),
)

internal open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

internal class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", "all"),
            Pair("Completed", "complete"),
            Pair("Ongoing", "in-process"),
            Pair("Hiatus", "pause"),
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Latest update", "lastest-chap"),
            Pair("Top all", "top-manga"),
            Pair("Hot", "hot"),
            Pair("New", "lastest-manga"),
            Pair("Top month", "top-month"),
            Pair("Top week", "top-week"),
            Pair("Top day", "top-day"),
            Pair("Follow", "follow"),
            Pair("Comment", "comment"),
            Pair("Num. Chapter", "num-chap"),
        ),
    )

internal class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All genres", ""),
            Pair("Adult", "adult"),
            Pair("Action", "action"),
            Pair("Adaptation", "adaptation"),
            Pair("Adventure", "adventure"),
            Pair("Anime", "anime"),
            Pair("Comedy", "comedy"),
            Pair("Completed", "completed"),
            Pair("Cooking", "cooking"),
            Pair("Crime", "crime"),
            Pair("Crossdressing", "crossdressin"),
            Pair("Delinquents", "delinquents"),
            Pair("Demons", "demons"),
            Pair("Detective", "detective"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Game", "game"),
            Pair("Ghosts", "ghosts"),
            Pair("Hentai", "hentai"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Magical", "magical"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Moder", "moder"),
            Pair("Monsters", "monsters"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Office Workers", "office-workers"),
            Pair("One shot", "one-shot"),
            Pair("Philosophical", "philosophical"),
            Pair("Police", "police"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Reverse", "reverse"),
            Pair("Reverse harem", "reverse-harem"),
            Pair("Romance", "romance"),
            Pair("Royal family", "royal-family"),
            Pair("Smut", "smut"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "scifi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Super power", "super-power"),
            Pair("Superhero", "superhero"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("Thriller", "thriller"),
            Pair("Time Travel", "time-travel"),
            Pair("Tragedy", "tragedy"),
            Pair("Vampire", "vampire"),
            Pair("Villainess", "villainess"),
            Pair("Webtoons", "webtoons"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Zombies", "zombies"),
        ),
    )
