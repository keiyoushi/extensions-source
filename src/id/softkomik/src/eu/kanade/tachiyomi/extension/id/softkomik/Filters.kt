package eu.kanade.tachiyomi.extension.id.softkomik

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected get() = options[state].second
}

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Tamat", "tamat"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Terbaru", "newKomik"),
            Pair("Popular", "popular"),
        ),
    )

class MinChapterFilter : Filter.Text("Minimal Chapter")

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            Pair("Semua Genre", ""),
            Pair("Action", "Action"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Cooking", "Cooking"),
            Pair("Crime", "Crime"),
            Pair("Demon", "demon"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Game", "Game"),
            Pair("Gender Bender", "Gender Bender"),
            Pair("Gore", "Gore"),
            Pair("Harem", "Harem"),
            Pair("Historical", "Historical"),
            Pair("Horror", "Horror"),
            Pair("Isekai", "Isekai"),
            Pair("Josei", "Josei"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "Martial Arts"),
            Pair("Mature", "Mature"),
            Pair("Mecha", "Mecha"),
            Pair("Medical", "Medical"),
            Pair("Military", "Military"),
            Pair("Music", "Musyc"),
            Pair("Mystery", "Mystery"),
            Pair("Parody", "parody"),
            Pair("Police", "Police"),
            Pair("Psychological", "Psychological"),
            Pair("Reincarnation", "Reincarnation"),
            Pair("Romance", "Romance"),
            Pair("School", "School"),
            Pair("School Life", "School Life"),
            Pair("Sci-fi", "Sci-fi"),
            Pair("Seinen", "Seinen"),
            Pair("Shoujo", "Shoujo"),
            Pair("Shoujo Ai", "Shoujo Ai"),
            Pair("Shounen", "Shounen"),
            Pair("Shounen Ai", "Shounen Ai"),
            Pair("Slice of Life", "Slice of Life"),
            Pair("Sports", "Sports"),
            Pair("Super Power", "Super Power"),
            Pair("Supernatural", "Supernatural"),
            Pair("Thriller", "Thriler"),
            Pair("Tragedy", "Tragedy"),
            Pair("Webtoons", "Webtoons"),
            Pair("Yaoi", "Yaoi"),
            Pair("Yuri", "Yuri"),
            Pair("Zombies", "zombies"),
        ),
    )
