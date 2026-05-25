package eu.kanade.tachiyomi.extension.en.mangabuddy

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val value: String) : Filter.TriState(name)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class MinChapterFilter : Filter.Text("Min Chapters")
class MaxChapterFilter : Filter.Text("Max Chapters")

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    val selected: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Latest Updated", "latest"),
            Pair("Recently Added", "newest"),
            Pair("Most Popular", "popular"),
            Pair("Highest Rating", "rating"),
            Pair("Most Viewed", "views"),
            Pair("Most Chapters", "chapters"),
            Pair("A-Z", "alphabetical"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("All Status", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("All Types", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demographics",
        arrayOf(
            Pair("All Demographics", ""),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
        ),
    )

fun getGenreList() = listOf(
    Genre("Academy", "academy"),
    Genre("Acting", "acting"),
    Genre("Action", "action"),
    Genre("Adaptation", "adaptation"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Adventure comedy", "adventure-comedy"),
    Genre("Animal", "animal"),
    Genre("Anthology", "anthology"),
    Genre("Apocalypse", "apocalypse"),
    Genre("Blood", "blood"),
    Genre("Business", "business"),
    Genre("Calm protagonist", "calm-protagonist"),
    Genre("Cartoon", "cartoon"),
    Genre("Cheat system", "cheat-system"),
    Genre("Comedy", "comedy"),
    Genre("Comic", "comic"),
    Genre("Conspiracy", "conspiracy"),
    Genre("Cooking", "cooking"),
    Genre("Crazy MC", "crazy-mc"),
    Genre("Delinquents", "delinquents"),
    Genre("Demons", "demons"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Dragon", "dragon"),
    Genre("Drama", "drama"),
    Genre("Dungeon", "dungeon"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Fantasy harem", "fantasy-harem"),
    Genre("Fight", "fight"),
    Genre("Fighting", "fighting"),
    Genre("Full Color", "full-color"),
    Genre("Game", "game"),
    Genre("Games", "games"),
    Genre("Gaming", "gaming"),
    Genre("Gender bender", "gender-bender"),
    Genre("Ghosts", "ghosts"),
    Genre("Gyaru", "gyaru"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Long strip", "long-strip"),
    Genre("Mafia", "mafia"),
    Genre("Magic", "magic"),
    Genre("Manga", "manga"),
    Genre("Mangatoon", "mangatoon"),
    Genre("Manhua", "manhua"),
    Genre("Manhwa", "manhwa"),
    Genre("Martial arts", "martial-arts"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Medical", "medical"),
    Genre("Military", "military"),
    Genre("Monster", "monster"),
    Genre("Monster girls", "monster-girls"),
    Genre("Monsters", "monsters"),
    Genre("Music", "music"),
    Genre("Mystery", "mystery"),
    Genre("Office", "office"),
    Genre("Office workers", "office-workers"),
    Genre("One shot", "one-shot"),
    Genre("OP-MC", "op-mc"),
    Genre("Otherworld", "otherworld"),
    Genre("Player", "player"),
    Genre("Police", "police"),
    Genre("Political", "political"),
    Genre("Psychological", "psychological"),
    Genre("Regression", "regression"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Return", "return"),
    Genre("Romance", "romance"),
    Genre("Ruthless protagonist", "ruthless-protagonist"),
    Genre("School life", "school-life"),
    Genre("Sci fi", "sci-fi"),
    Genre("Science fiction", "science-fiction"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen ai", "shounen-ai"),
    Genre("Slice of life", "slice-of-life"),
    Genre("Smart MC", "smart-mc"),
    Genre("Smut", "smut"),
    Genre("Soft Yaoi", "soft-yaoi"),
    Genre("Sports", "sports"),
    Genre("Super Power", "super-power"),
    Genre("Superhero", "superhero"),
    Genre("Supernatural", "supernatural"),
    Genre("Sword and magic", "sword-and-magic"),
    Genre("Terror", "terror"),
    Genre("Thriller", "thriller"),
    Genre("Time travel", "time-travel"),
    Genre("Tower", "tower"),
    Genre("Tragedy", "tragedy"),
    Genre("Transmigrating", "transmigrating"),
    Genre("Vampire", "vampire"),
    Genre("Vampires", "vampires"),
    Genre("Video games", "video-games"),
    Genre("Villain", "villain"),
    Genre("Villainess", "villainess"),
    Genre("Virtual reality", "virtual-reality"),
    Genre("Weak to strong", "weak-to-strong"),
    Genre("Web comic", "web-comic"),
    Genre("Webtoons", "webtoons"),
    Genre("Work-life", "work-life"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
    Genre("Zombies", "zombies"),
)
