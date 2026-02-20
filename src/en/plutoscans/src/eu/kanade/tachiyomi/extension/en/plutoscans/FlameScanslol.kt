package eu.kanade.tachiyomi.extension.en.plutoscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FlameScanslol :
    Madara(
        "FlameScans.lol",
        "https://flamescans.lol",
        "en",
    ) {
    override val id = 1001157238479601077

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val fetchGenres = false
    override var genresList = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Aging", "aging"),
        Genre("Boys", "boys"),
        Genre("Cartoon", "cartoon"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Completed", "completed"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Fighting", "fighting"),
        Genre("Full color", "full-color"),
        Genre("Future", "future"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Girls", "girls"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Live action", "live-action"),
        Genre("Love", "love"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Mystery", "mystery"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoon", "webtoon"),
    )
}
