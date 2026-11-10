package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Urutkan",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = SORT_OPTIONS[state].second

    companion object {
        private val SORT_OPTIONS = listOf(
            Pair("Relevance", "relevance"),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Most Views", "views"),
            Pair("New", "new"),
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = STATUS_OPTIONS[state].second

    companion object {
        private val STATUS_OPTIONS = listOf(
            Pair("Semua", ""),
            Pair("Ongoing", "on-going"),
            Pair("Completed", "end"),
            Pair("Hiatus", "on-hold"),
        )
    }
}

class OperatorFilter :
    Filter.Select<String>(
        "Cocokan Genre",
        OPERATOR_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun getValue() = OPERATOR_OPTIONS[state].second

    companion object {
        private val OPERATOR_OPTIONS = listOf(
            Pair("Match ANY", ""),
            Pair("Match ALL", "1"),
        )
    }
}

class TypeFilter :
    Filter.Group<TypeCheckBox>(
        "Tipe",
        listOf(
            TypeCheckBox("Manga", "manga"),
            TypeCheckBox("Manhua", "manhua"),
            TypeCheckBox("Manhwa", "manhwa"),
        ),
    )

class TypeCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Genre",
        GENRE_LIST.map { GenreCheckBox(it.first, it.second) },
    )

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

private val GENRE_LIST = listOf(
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Animals", "animals"),
    Pair("Apocalypse", "apocalypse"),
    Pair("Childhood Friends", "childhood-friends"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Demon", "demon"),
    Pair("Demons", "demons"),
    Pair("Drama", "drama"),
    Pair("Dungeons", "dungeons"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Fight", "fight"),
    Pair("Fighting", "fighting"),
    Pair("Full Color", "full-color"),
    Pair("Game", "game"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Gore", "gore"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Josei(W)", "joseiw"),
    Pair("Kids", "kids"),
    Pair("Magic", "magic"),
    Pair("Manga", "manga"),
    Pair("Mangatoon", "mangatoon"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Modern", "modern"),
    Pair("Monsters", "monsters"),
    Pair("Murim", "murim"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Office Workers", "office-workers"),
    Pair("One-Shot", "one-shot"),
    Pair("Overpowered", "overpowered"),
    Pair("Police", "police"),
    Pair("Psychological", "psychological"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Revenge", "revenge"),
    Pair("Reverse Harem", "reverse-harem"),
    Pair("Rofan", "rofan"),
    Pair("Romance", "romance"),
    Pair("Royal family", "royal-family"),
    Pair("School", "school"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo AI", "shouai"),
    Pair("Shoujo(G)", "shoujog"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Super Power", "super-power"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("System", "system"),
    Pair("Thriller", "thriller"),
    Pair("Time Travel", "time-travel"),
    Pair("Tragedy", "tragedy"),
    Pair("Transmigration", "transmigration"),
    Pair("Vampire", "vampire"),
    Pair("Villainess", "villainess"),
    Pair("Violence", "violence"),
    Pair("War", "war"),
    Pair("Webtoon", "webtoon"),
    Pair("Webtoons", "webtoons"),
    Pair("Wuxia", "wuxia"),
    Pair("Yuri", "yuri"),
)
