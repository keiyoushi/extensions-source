package eu.kanade.tachiyomi.extension.en.nixmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "Any" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoons" to "webtoon",
            "Pornhwa" to "pornhwa",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        ),
    )

class DemographicFilter :
    UriPartFilter(
        "Demographic",
        arrayOf(
            "Any" to "",
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
        ),
    )

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf(
            "Latest Upload",
            "Most Popular",
            "Top Rated",
            "Alphabetical",
            "Most Chapters",
            "Oldest",
        ),
        Selection(0, false),
    )

class YearFilter : Filter.Text("Year")

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

fun getGenreList() = listOf(
    Genre("Action", "action"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Aliens", "aliens"),
    Genre("Animals", "animals"),
    Genre("Boys Love", "boys-love"),
    Genre("Comedy", "comedy"),
    Genre("Cooking", "cooking"),
    Genre("Crime", "crime"),
    Genre("Crossdressing", "crossdressing"),
    Genre("Delinquents", "delinquents"),
    Genre("Demons", "demons"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Genderswap", "genderswap"),
    Genre("Ghosts", "ghosts"),
    Genre("Girls Love", "girls-love"),
    Genre("Gyaru", "gyaru"),
    Genre("Harem", "harem"),
    Genre("Hentai", "hentai"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Incest", "incest"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Loli", "loli"),
    Genre("Mafia", "mafia"),
    Genre("Magic", "magic"),
    Genre("Magical Girls", "magical-girls"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Medical", "medical"),
    Genre("Military", "military"),
    Genre("Monster Girls", "monster-girls"),
    Genre("Monsters", "monsters"),
    Genre("Music", "music"),
    Genre("Mystery", "mystery"),
    Genre("Ninja", "ninja"),
    Genre("Office Workers", "office-workers"),
    Genre("Philosophical", "philosophical"),
    Genre("Police", "police"),
    Genre("Post-Apocalyptic", "post-apocalyptic"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Reverse Harem", "reverse-harem"),
    Genre("Romance", "romance"),
    Genre("Samurai", "samurai"),
    Genre("School Life", "school-life"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shota", "shota"),
    Genre("Shoujo", "shoujo"),
    Genre("Shounen", "shounen"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Superhero", "superhero"),
    Genre("Supernatural", "supernatural"),
    Genre("Survival", "survival"),
    Genre("Thriller", "thriller"),
    Genre("Time Travel", "time-travel"),
    Genre("Traditional Games", "traditional-games"),
    Genre("Tragedy", "tragedy"),
    Genre("Vampires", "vampires"),
    Genre("Video Games", "video-games"),
    Genre("Villainess", "villainess"),
    Genre("Virtual Reality", "virtual-reality"),
    Genre("Wuxia", "wuxia"),
    Genre("Zombies", "zombies"),
)
