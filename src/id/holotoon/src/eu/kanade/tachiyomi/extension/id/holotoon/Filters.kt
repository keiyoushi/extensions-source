package eu.kanade.tachiyomi.extension.id.holotoon

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Urutkan berdasarkan",
        arrayOf(
            Pair("Terbaru", "latest"),
            Pair("Terpopuler", "popular"),
            Pair("Rating", "rating"),
            Pair("A-Z", "az"),
        ),
    )

class MediaFilter :
    UriPartFilter(
        "Media",
        arrayOf(
            Pair("Komik & Novel", ""),
            Pair("Komik", "comic"),
            Pair("Novel", "novel"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        arrayOf(
            Pair("Semua Tipe", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Comic", "comic"),
            Pair("Webtoon", "webtoon"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Semua Status", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Semua Genre", ""),
            Pair("Action", "action"),
            Pair("Adaptation", "adaptation"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Age Gap", "age-gap"),
            Pair("Aliens", "aliens"),
            Pair("Animals", "animals"),
            Pair("Anthology", "anthology"),
            Pair("Bara(ML)", "baraml"),
            Pair("Beasts", "beasts"),
            Pair("Bloody", "bloody"),
            Pair("Bodyswap", "bodyswap"),
            Pair("Boys", "boys"),
            Pair("Cars", "cars"),
            Pair("Cheating/Infidelity", "cheatinginfidelity"),
            Pair("Childhood Friends", "childhood-friends"),
            Pair("College life", "college-life"),
            Pair("Comedy", "comedy"),
            Pair("Contest winning", "contest-winning"),
            Pair("Cooking", "cooking"),
            Pair("Crime", "crime"),
            Pair("Crossdressing", "crossdressing"),
            Pair("Delinquents", "delinquents"),
            Pair("Dementia", "dementia"),
            Pair("Demons", "demons"),
            Pair("Drama", "drama"),
            Pair("Dungeons", "dungeons"),
            Pair("Ecchi", "ecchi"),
            Pair("Emperor's daughter", "emperors-daughter"),
            Pair("Fan-Colored", "fan-colored"),
            Pair("Fantasy", "fantasy"),
            Pair("Fetish", "fetish"),
            Pair("Full Color", "full-color"),
            Pair("Game", "game"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Genderswap", "genderswap"),
            Pair("Ghosts", "ghosts"),
            Pair("Girls", "girls"),
            Pair("Gore", "gore"),
            Pair("Gyaru", "gyaru"),
            Pair("Harem", "harem"),
            Pair("Harlequin", "harlequin"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Incest", "incest"),
            Pair("Isekai", "isekai"),
            Pair("Josei(W)", "joseiw"),
            Pair("Kids", "kids"),
            Pair("Kodomo(Kid)", "kodomokid"),
            Pair("Magic", "magic"),
            Pair("Magical Girls", "magical-girls"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Monster Girls", "monster-girls"),
            Pair("Monsters", "monsters"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Netorare/NTR", "netorarentr"),
            Pair("Ninja", "ninja"),
            Pair("Non-human", "non-human"),
            Pair("Office Workers", "office-workers"),
            Pair("Omegaverse", "omegaverse"),
            Pair("Parody", "parody"),
            Pair("Philosophical", "philosophical"),
            Pair("Police", "police"),
            Pair("Post-Apocalyptic", "post-apocalyptic"),
            Pair("Psychological", "psychological"),
            Pair("Regression", "regression"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Revenge", "revenge"),
            Pair("Reverse Harem", "reverse-harem"),
            Pair("Reverse Isekai", "reverse-isekai"),
            Pair("Romance", "romance"),
            Pair("Royal family", "royal-family"),
            Pair("Royalty", "royalty"),
            Pair("Samurai", "samurai"),
            Pair("School Life", "school-life"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen(M)", "seinenm"),
            Pair("Shoujo ai", "shoujo-ai"),
            Pair("Shoujo(G)", "shoujog"),
            Pair("Shounen ai", "shounen-ai"),
            Pair("Shounen(B)", "shounenb"),
            Pair("Showbiz", "showbiz"),
            Pair("Silver & Golden", "silver--golden"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("SM/BDSM/SUB-DOM", "smbdsmsub-dom"),
            Pair("Smut", "smut"),
            Pair("Space", "space"),
            Pair("Sports", "sports"),
            Pair("Super Power", "super-power"),
            Pair("Superhero", "superhero"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("Thriller", "thriller"),
            Pair("Time Travel", "time-travel"),
            Pair("Tower Climbing", "tower-climbing"),
            Pair("Traditional Games", "traditional-games"),
            Pair("Tragedy", "tragedy"),
            Pair("Transmigration", "transmigration"),
            Pair("Vampires", "vampires"),
            Pair("Video Games", "video-games"),
            Pair("Villainess", "villainess"),
            Pair("Violence", "violence"),
            Pair("Virtual Reality", "virtual-reality"),
            Pair("Wuxia", "wuxia"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan"),
            Pair("Yakuzas", "yakuzas"),
            Pair("Yaoi(BL)", "yaoibl"),
            Pair("Yuri(GL)", "yurigl"),
            Pair("Zombies", "zombies"),
        ),
    )

class TeamFilter :
    UriPartFilter(
        "Tim",
        arrayOf(
            Pair("Semua Tim", ""),
            Pair("Moccacinno", "moccacinno"),
            Pair("Pramudya Dukedom", "pramudya-dukedom"),
        ),
    )
