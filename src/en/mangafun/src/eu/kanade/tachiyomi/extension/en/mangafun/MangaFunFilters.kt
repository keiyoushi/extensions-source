package eu.kanade.tachiyomi.extension.en.mangafun

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Group<Genre>("Genre", genreList)

class TypeFilter : Filter.Group<Genre>("Type", titleTypeList)

class StatusFilter : Filter.Group<Genre>(
    "Status",
    listOf("Ongoing", "Completed", "Hiatus", "Cancelled").mapIndexed { i, it -> Genre(it, i) },
)

class SortFilter : Filter.Sort(
    "Order by",
    arrayOf("Name", "Rank", "Newest", "Update"),
    Selection(1, false),
)

class Genre(name: String, val id: Int) : Filter.TriState(name)

val genresMap by lazy {
    genreList.associate { it.id to it.name }
}

val titleTypeMap by lazy {
    titleTypeList.associate { it.id to it.name }
}

val titleTypeList by lazy {
    listOf(
        Genre("Manga", 0),
        Genre("Manhwa", 1),
        Genre("Manhua", 2),
        Genre("Comic", 3),
        Genre("Webtoon", 4),
        Genre("One Shot", 6),
        Genre("Doujinshi", 7),
        Genre("Other", 8),
    )
}

val genreList by lazy {
    listOf(
        Genre("Supernatural", 1),
        Genre("Action", 2),
        Genre("Comedy", 3),
        Genre("Josei", 4),
        Genre("Martial Arts", 5),
        Genre("Romance", 6),
        Genre("Ecchi", 7),
        Genre("Harem", 8),
        Genre("School Life", 9),
        Genre("Seinen", 10),
        Genre("Adventure", 11),
        Genre("Fantasy", 12),
        Genre("Demons", 13),
        Genre("Magic", 14),
        Genre("Military", 15),
        Genre("Shounen", 16),
        Genre("Shoujo", 17),
        Genre("Psychological", 18),
        Genre("Drama", 19),
        Genre("Mystery", 20),
        Genre("Sci-Fi", 21),
        Genre("Slice of Life", 22),
        Genre("Doujinshi", 23),
        Genre("Police", 24),
        Genre("Mecha", 25),
        Genre("Yaoi", 26),
        Genre("Horror", 27),
        Genre("Historical", 28),
        Genre("Thriller", 29),
        Genre("Shounen Ai", 30),
        Genre("Game", 31),
        Genre("Gender Bender", 32),
        Genre("Sports", 33),
        Genre("Yuri", 34),
        Genre("Music", 35),
        Genre("Shoujo Ai", 36),
        Genre("Vampires", 37),
        Genre("Parody", 38),
        Genre("Kids", 40),
        Genre("Super Power", 41),
        Genre("Space", 43),
        Genre("Adult", 46),
        Genre("Webtoons", 47),
        Genre("Mature", 48),
        Genre("Smut", 49),
        Genre("Tragedy", 51),
        Genre("One Shot", 53),
        Genre("4-koma", 56),
        Genre("Isekai", 58),
        Genre("Food", 60),
        Genre("Crime", 63),
        Genre("Superhero", 67),
        Genre("Animals", 69),
        Genre("Manhwa", 74),
        Genre("Manhua", 75),
        Genre("Cooking", 78),
        Genre("Medical", 79),
        Genre("Magical Girls", 88),
        Genre("Monsters", 89),
        Genre("Shotacon", 90),
        Genre("Philosophical", 91),
        Genre("Wuxia", 92),
        Genre("Adaptation", 95),
        Genre("Full Color", 96),
        Genre("Korean", 97),
        Genre("Chinese", 98),
        Genre("Reincarnation", 100),
        Genre("Manga", 102),
        Genre("Comic", 104),
        Genre("Japanese", 105),
        Genre("Time Travel", 108),
        Genre("Erotica", 111),
        Genre("Survival", 114),
        Genre("Gore", 118),
        Genre("Monster Girls", 120),
        Genre("Dungeons", 123),
        Genre("System", 124),
        Genre("Cultivation", 125),
        Genre("Murim", 128),
        Genre("Suggestive", 131),
        Genre("Fighting", 134),
        Genre("Blood", 140),
        Genre("Op-Mc", 142),
        Genre("Revenge", 144),
        Genre("Overpowered", 146),
        Genre("Returner", 150),
        Genre("Office", 152),
        Genre("Loli", 163),
        Genre("Video Games", 173),
        Genre("Monster", 199),
        Genre("Mafia", 203),
        Genre("Anthology", 206),
        Genre("Villainess", 207),
        Genre("Aliens", 213),
        Genre("Zombies", 216),
        Genre("Violence", 217),
        Genre("Delinquents", 219),
        Genre("Post apocalyptic", 255),
        Genre("Ghost", 260),
        Genre("Virtual Reality", 263),
        Genre("Cheat", 324),
        Genre("Girls", 374),
        Genre("Gender Swap", 384),
    )
}
