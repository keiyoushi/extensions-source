package eu.kanade.tachiyomi.extension.en.comickfan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

private val formatGenres = listOf(
    Genre("Award Winning", "award-winning"),
    Genre("Long Strip", "long-strip"),
    Genre("Official Colored", "official-colored"),
    Genre("Fan Colored", "fan-colored"),
    Genre("Anthology", "anthology"),
    Genre("Full Color", "full-color"),
    Genre("4-Koma", "4-koma"),
    Genre("User Created", "user-created"),
    Genre("Adaptation", "adaptation"),
    Genre("Web Comic", "web-comic"),
    Genre("Oneshot", "oneshot"),
    Genre("Doujinshi", "doujinshi"),
)
private val contentGenres = listOf(
    Genre("Sexual Violence", "sexual-violence"),
    Genre("Gore", "gore"),
    Genre("Smut", "smut"),
    Genre("Ecchi", "ecchi"),
)

private val themeGenres = listOf(
    Genre("Ninja", "ninja"),
    Genre("Virtual Reality", "virtual-reality"),
    Genre("Police", "police"),
    Genre("Magic", "magic"),
    Genre("Villainess", "villainess"),
    Genre("Traditional Games", "traditional-games"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Zombies", "zombies"),
    Genre("Loli", "loli"),
    Genre("Time Travel", "time-travel"),
    Genre("Mafia", "mafia"),
    Genre("Music", "music"),
    Genre("Monsters", "monsters"),
    Genre("Post-Apocalyptic", "post-apocalyptic"),
    Genre("Office Workers", "office-workers"),
    Genre("Monster Girls", "monster-girls"),
    Genre("Cooking", "cooking"),
    Genre("Video Games", "video-games"),
    Genre("Reverse Harem", "reverse-harem"),
    Genre("Demons", "demons"),
    Genre("Harem", "harem"),
    Genre("Vampires", "vampires"),
    Genre("Shota", "shota"),
    Genre("Incest", "incest"),
    Genre("Delinquents", "delinquents"),
    Genre("Gyaru", "gyaru"),
    Genre("Animals", "animals"),
    Genre("Military", "military"),
    Genre("Aliens", "aliens"),
    Genre("Survival", "survival"),
    Genre("Ghosts", "ghosts"),
    Genre("Crossdressing", "crossdressing"),
    Genre("School Life", "school-life"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Samurai", "samurai"),
    Genre("Genderswap", "genderswap"),
    Genre("Supernatural", "supernatural"),
)

private val genreGenres = listOf(
    Genre("Fantasy", "fantasy"),
    Genre("Wuxia", "wuxia"),
    Genre("Drama", "drama"),
    Genre("Sports", "sports"),
    Genre("Psychological", "psychological"),
    Genre("Medical", "medical"),
    Genre("Superhero", "superhero"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Romance", "romance"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Tragedy", "tragedy"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Isekai", "isekai"),
    Genre("Mecha", "mecha"),
    Genre("Adult", "adult"),
    Genre("Magical Girls", "magical-girls"),
    Genre("Philosophical", "philosophical"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Thriller", "thriller"),
    Genre("Historical", "historical"),
    Genre("Yaoi", "yaoi"),
    Genre("Mature", "mature"),
    Genre("Mystery", "mystery"),
    Genre("Adventure", "adventure"),
    Genre("Yuri", "yuri"),
    Genre("Comedy", "comedy"),
    Genre("Horror", "horror"),
    Genre("Others", "others"),
    Genre("Crime", "crime"),
    Genre("Action", "action"),
)

open class UriPartFilter(name: String, private val options: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = options[this.state].second
}

val Filter.Group<Genre>.selected get() = state.filter(Genre::state).map { it.value }

class FormatGenreFilter : Filter.Group<Genre>("Format", formatGenres)
class ContentGenreFilter : Filter.Group<Genre>("Content", contentGenres)
class ThemeGenreFilter : Filter.Group<Genre>("Theme", themeGenres)
class GenreGenreFilter : Filter.Group<Genre>("Genre", genreGenres)

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "1",
            "Completed" to "2",
            "Cancelled" to "3",
            "Hiatus" to "4",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Manga" to "jp",
            "Manhwa" to "kr",
            "Manhua" to "cn",
        ),
    )

class SortFilter(state: Int = 0) :
    UriPartFilter(
        "Order By",
        arrayOf(
            "All" to "",
            "Last Updated" to "latest",
            "Rating" to "rating",
            "Bookmark Count" to "bookmark",
            "Name (A-Z)" to "name_asc",
            "Name (Z-A)" to "name_desc",
        ),
        state,
    ) {
    companion object {
        val POPULAR = FilterList(SortFilter(2))
        val LATEST = FilterList(SortFilter(1))
    }
}
