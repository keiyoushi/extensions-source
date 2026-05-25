package eu.kanade.tachiyomi.extension.en.mangabuddy

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val value: String, state: Int = STATE_IGNORE) : Filter.TriState(name, state)
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

fun getGenreList(blacklist: Set<String> = emptySet()) = listOf(
    Pair("Academy", "academy"),
    Pair("Acting", "acting"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Adventure comedy", "adventure-comedy"),
    Pair("Animal", "animal"),
    Pair("Anthology", "anthology"),
    Pair("Apocalypse", "apocalypse"),
    Pair("Blood", "blood"),
    Pair("Business", "business"),
    Pair("Calm protagonist", "calm-protagonist"),
    Pair("Cartoon", "cartoon"),
    Pair("Cheat system", "cheat-system"),
    Pair("Comedy", "comedy"),
    Pair("Comic", "comic"),
    Pair("Conspiracy", "conspiracy"),
    Pair("Cooking", "cooking"),
    Pair("Crazy MC", "crazy-mc"),
    Pair("Delinquents", "delinquents"),
    Pair("Demons", "demons"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Dragon", "dragon"),
    Pair("Drama", "drama"),
    Pair("Dungeon", "dungeon"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Fantasy harem", "fantasy-harem"),
    Pair("Fight", "fight"),
    Pair("Fighting", "fighting"),
    Pair("Full Color", "full-color"),
    Pair("Game", "game"),
    Pair("Games", "games"),
    Pair("Gaming", "gaming"),
    Pair("Gender bender", "gender-bender"),
    Pair("Ghosts", "ghosts"),
    Pair("Gyaru", "gyaru"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Long strip", "long-strip"),
    Pair("Mafia", "mafia"),
    Pair("Magic", "magic"),
    Pair("Manga", "manga"),
    Pair("Mangatoon", "mangatoon"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Monster", "monster"),
    Pair("Monster girls", "monster-girls"),
    Pair("Monsters", "monsters"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Office", "office"),
    Pair("Office workers", "office-workers"),
    Pair("One shot", "one-shot"),
    Pair("OP-MC", "op-mc"),
    Pair("Otherworld", "otherworld"),
    Pair("Player", "player"),
    Pair("Police", "police"),
    Pair("Political", "political"),
    Pair("Psychological", "psychological"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Return", "return"),
    Pair("Romance", "romance"),
    Pair("Ruthless protagonist", "ruthless-protagonist"),
    Pair("School life", "school-life"),
    Pair("Sci fi", "sci-fi"),
    Pair("Science fiction", "science-fiction"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo ai", "shoujo-ai"),
    Pair("Shounen", "shounen"),
    Pair("Shounen ai", "shounen-ai"),
    Pair("Slice of life", "slice-of-life"),
    Pair("Smart MC", "smart-mc"),
    Pair("Smut", "smut"),
    Pair("Soft Yaoi", "soft-yaoi"),
    Pair("Sports", "sports"),
    Pair("Super Power", "super-power"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Sword and magic", "sword-and-magic"),
    Pair("Terror", "terror"),
    Pair("Thriller", "thriller"),
    Pair("Time travel", "time-travel"),
    Pair("Tower", "tower"),
    Pair("Tragedy", "tragedy"),
    Pair("Transmigrating", "transmigrating"),
    Pair("Vampire", "vampire"),
    Pair("Vampires", "vampires"),
    Pair("Video games", "video-games"),
    Pair("Villain", "villain"),
    Pair("Villainess", "villainess"),
    Pair("Virtual reality", "virtual-reality"),
    Pair("Weak to strong", "weak-to-strong"),
    Pair("Web comic", "web-comic"),
    Pair("Webtoons", "webtoons"),
    Pair("Work-life", "work-life"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
    Pair("Zombies", "zombies"),
).map { (name, value) ->
    Genre(name, value, if (value in blacklist) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE)
}
