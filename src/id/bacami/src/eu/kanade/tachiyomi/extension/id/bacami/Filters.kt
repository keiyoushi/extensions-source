package eu.kanade.tachiyomi.extension.id.bacami

import eu.kanade.tachiyomi.source.model.Filter

class NewKomikFilter : Filter.CheckBox("Komik Baru")

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class OrderByFilter :
    UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Latest Updates", "latest"),
            Pair("Alphabetical", "name"),
            Pair("Score", "score"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", "all"),
            Pair("Hot", "hot"),
            Pair("Project", "project"),
            Pair("Completed", "tamat"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", "all"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", "all"),
            Pair("Action", "action-2"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Apocalypse", "apocalypse"),
            Pair("Comedy", "comedy"),
            Pair("Comedy Mystery Romance Slice Of Life Supernatural", "comedy-mystery-romance-slice-of-life-supernatural"),
            Pair("Comedy Romance Slice Of Life", "comedy-romance-slice-of-life"),
            Pair("Cooking", "cooking"),
            Pair("Crime", "crime"),
            Pair("Cultivation", "cultivation"),
            Pair("Demons", "demons"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Furry", "furry"),
            Pair("Game", "game"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Genius", "genius"),
            Pair("Gore", "gore"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Lolicon", "lolicon"),
            Pair("Long Strip", "long-strip"),
            Pair("Love Polygon", "love-polygon"),
            Pair("Magic", "magic"),
            Pair("Magical Girl", "magical-girl"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Art", "martial-art"),
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
            Pair("Mystery Shounen", "mystery-shounen"),
            Pair("Mythology", "mythology"),
            Pair("One Shot", "one-shot"),
            Pair("Oneshot", "oneshot"),
            Pair("Parody", "parody"),
            Pair("Philosophical", "philosophical"),
            Pair("Police", "police"),
            Pair("Post-Apocalyptic", "post-apocalyptic"),
            Pair("Psychological", "psychological"),
            Pair("Rebirth", "rebirth"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("Romantic Subtext", "romantic-subtext"),
            Pair("Samurai", "samurai"),
            Pair("School", "school"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shotacon", "shotacon"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Space", "space"),
            Pair("Sports", "sports"),
            Pair("Superhero", "superhero"),
            Pair("Supernatural", "supernatural"),
            Pair("Super Power", "super-power"),
            Pair("Survival", "survival"),
            Pair("Suspense", "suspense"),
            Pair("System", "system"),
            Pair("Team Sports", "team-sports"),
            Pair("Thriller", "thriller"),
            Pair("Time Travel", "time-travel"),
            Pair("Tragedy", "tragedy"),
            Pair("Urban", "urban"),
            Pair("Urban Fantasy", "urban-fantasy"),
            Pair("Vampire", "vampire"),
            Pair("Video Game", "video-game"),
            Pair("Villainess", "villainess"),
            Pair("Visual Arts", "visual-arts"),
            Pair("Webtoon", "webtoon"),
            Pair("Webtoons", "webtoons"),
            Pair("Wuxia", "wuxia"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Zombies", "zombies"),
        ),
    )
