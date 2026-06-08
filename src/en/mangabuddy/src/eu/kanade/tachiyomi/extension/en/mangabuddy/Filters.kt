package eu.kanade.tachiyomi.extension.en.mangabuddy

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val value: String, state: Int = STATE_IGNORE) : Filter.TriState(name, state)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class AuthorFilter : Filter.Text("Author")
class MinChapterFilter : Filter.Text("Min Chapters")

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
            Pair("Best Match", ""),
            Pair("Latest Updated", "latest"),
            Pair("Recently Added", "newest"),
            Pair("Most Followed", "popular"),
            Pair("Highest Rating", "rating"),
            Pair("Most Viewed: Today", "views_today"),
            Pair("Most Viewed: 7 Days", "views_7days"),
            Pair("Most Viewed: 30 Days", "views_30days"),
            Pair("Most Viewed: All Time", "views"),
            Pair("Most Chapters", "chapters"),
            Pair("A-Z", "alphabetical"),
        ),
    )

class ContentRatingFilter :
    SelectFilter(
        "Content Rating",
        arrayOf(
            Pair("Any", ""),
            Pair("Safe", "safe"),
            Pair("Suggestive", "suggestive"),
            Pair("Erotica", "erotica"),
            Pair("Pornographic", "pornographic"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("Any", ""),
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
            Pair("Any", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demographics",
        arrayOf(
            Pair("Any", ""),
            Pair("Boy (Shounen + Seinen)", "shounen,seinen"),
            Pair("Girl (Shoujo + Josei)", "shoujo,josei"),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
        ),
    )

fun getGenreList(blacklist: Set<String> = emptySet()) = listOf(
    Pair("4-Koma", "4-koma"),
    Pair("Academy", "academy"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Anthology", "anthology"),
    Pair("Blood", "blood"),
    Pair("Bloody", "bloody"),
    Pair("Boys Love", "boys-love"),
    Pair("Bully", "bully"),
    Pair("Business", "business"),
    Pair("Cheat", "cheat"),
    Pair("Cheat System", "cheat-system"),
    Pair("Cheat Systems", "cheat-systems"),
    Pair("Comedy", "comedy"),
    Pair("Comic", "comic"),
    Pair("Cooking", "cooking"),
    Pair("Cultivation", "cultivation"),
    Pair("Dark Lord", "dark-lord"),
    Pair("Delinquents", "delinquents"),
    Pair("Demons", "demons"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Dungeon", "dungeon"),
    Pair("Dungeons", "dungeons"),
    Pair("Ecchi", "ecchi"),
    Pair("Erotica", "erotica"),
    Pair("Fantasy", "fantasy"),
    Pair("Fight", "fight"),
    Pair("Fighting", "fighting"),
    Pair("Full Color", "full-color"),
    Pair("Future Era", "future-era"),
    Pair("Game", "game"),
    Pair("Gaming", "gaming"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Ghosts", "ghosts"),
    Pair("Girls Love", "girls-love"),
    Pair("Gourmet", "gourmet"),
    Pair("Harem", "harem"),
    Pair("Hentai", "hentai"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Hunters", "hunters"),
    Pair("Isekai", "isekai"),
    Pair("Iyashikei", "iyashikei"),
    Pair("Josei", "josei"),
    Pair("Kids", "kids"),
    Pair("Long Strip", "long-strip"),
    Pair("Magic", "magic"),
    Pair("Magical Girls", "magical-girls"),
    Pair("Mahou Shoujo", "mahou-shoujo"),
    Pair("Male Protagonists", "male_protagonists"),
    Pair("Manga", "manga"),
    Pair("Mangatoon", "mangatoon"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Monster", "monster"),
    Pair("Monster Girls", "monster-girls"),
    Pair("Monsters", "monsters"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Necromancer", "necromancer"),
    Pair("Office Workers", "office-workers"),
    Pair("One Shot", "one-shot"),
    Pair("OP-MC", "op-mc"),
    Pair("Otherworld", "otherworld"),
    Pair("Overpowered", "overpowered"),
    Pair("Parody", "parody"),
    Pair("Philosophical", "philosophical"),
    Pair("Player", "player"),
    Pair("Police", "police"),
    Pair("Political", "political"),
    Pair("Psychological", "psychological"),
    Pair("Rebirth", "rebirth"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Returner", "returner"),
    Pair("Romance", "romance"),
    Pair("Royal Family", "royal-family"),
    Pair("Royalty", "royalty"),
    Pair("Ruthless Protagonist", "ruthless-protagonist"),
    Pair("Safe", "safe"),
    Pair("School Life", "school-life"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Science Fiction", "science-fiction"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shounen", "shounen"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Slaves", "slaves"),
    Pair("Slice Of Life", "slice-of-life"),
    Pair("Smart Mc", "smart-mc"),
    Pair("Smut", "smut"),
    Pair("Soft Yaoi", "soft-yaoi"),
    Pair("Space", "space"),
    Pair("Sports", "sports"),
    Pair("Super Power", "super-power"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Suspense", "suspense"),
    Pair("Swords", "swords"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Tower", "tower"),
    Pair("Tragedy", "tragedy"),
    Pair("Vampire", "vampire"),
    Pair("Vampires", "vampires"),
    Pair("Video Games", "video-games"),
    Pair("Villain", "villain"),
    Pair("Villainess", "villainess"),
    Pair("Virtual Reality", "virtual-reality"),
    Pair("Violence", "voilence"),
    Pair("Web Comic", "web-comic"),
    Pair("Webtoons", "webtoons"),
    Pair("Work-Life", "work-life"),
    Pair("Yaoi", "yaoi"),
    Pair("Yaoi (BL)", "yaoi-bl"),
    Pair("Yuri", "yuri"),
    Pair("Zombies", "zombies"),
).map { (name, value) ->
    Genre(name, value, if (value in blacklist) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE)
}
