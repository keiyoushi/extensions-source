package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.source.model.Filter

internal class CheckBoxVal(name: String, val value: String) : Filter.CheckBox(name)

internal class TypeFilter :
    Filter.Group<CheckBoxVal>(
        "Type",
        listOf(
            CheckBoxVal("Manga", "manga"),
            CheckBoxVal("Manhwa", "manhwa"),
            CheckBoxVal("Manhua", "manhua"),
            CheckBoxVal("Webtoon", "webtoon"),
            CheckBoxVal("Comic", "comic"),
        ),
    )

internal class StatusFilter :
    Filter.Group<CheckBoxVal>(
        "Status",
        listOf(
            CheckBoxVal("On Going", "on_going"),
            CheckBoxVal("Completed", "completed"),
            CheckBoxVal("On Hold", "on_hold"),
            CheckBoxVal("Canceled", "canceled"),
        ),
    )

internal class ContentRatingFilter :
    Filter.Group<CheckBoxVal>(
        "Content rating",
        listOf(
            CheckBoxVal("Safe", "safe"),
            CheckBoxVal("Suggestive", "suggestive"),
            CheckBoxVal("Mature", "mature"),
            CheckBoxVal("Erotica", "erotica"),
        ),
    )

internal class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf(
            "Recently Updated",
            "Trending",
            "Most Viewed",
            "Highest Rated",
            "Alphabetical",
            "New Manga",
        ),
        Selection(0, false),
    )

internal class GenreFilter :
    Filter.Group<CheckBoxVal>(
        "Genre",
        listOf(
            CheckBoxVal("Action", "action"),
            CheckBoxVal("Adventure", "adventure"),
            CheckBoxVal("Comedy", "comedy"),
            CheckBoxVal("Drama", "drama"),
            CheckBoxVal("Ecchi", "ecchi"),
            CheckBoxVal("Fantasy", "fantasy"),
            CheckBoxVal("Gourmet", "gourmet"),
            CheckBoxVal("Harem", "harem"),
            CheckBoxVal("Historical", "historical"),
            CheckBoxVal("Isekai", "isekai"),
            CheckBoxVal("Josei", "josei"),
            CheckBoxVal("Magic", "magic"),
            CheckBoxVal("Martial Arts", "martial-arts"),
            CheckBoxVal("Monsters", "monsters"),
            CheckBoxVal("Music", "music"),
            CheckBoxVal("Mystery", "mystery"),
            CheckBoxVal("Psychological", "psychological"),
            CheckBoxVal("Regression", "regression"),
            CheckBoxVal("Romance", "romance"),
            CheckBoxVal("School Life", "school-life"),
            CheckBoxVal("Sci-Fi", "sci-fi"),
            CheckBoxVal("Seinen", "seinen"),
            CheckBoxVal("Shoujo", "shoujo"),
            CheckBoxVal("Shounen", "shounen"),
            CheckBoxVal("Slice of Life", "slice-of-life"),
            CheckBoxVal("Supernatural", "supernatural"),
            CheckBoxVal("Survival", "survival"),
            CheckBoxVal("Tragedy", "tragedy"),
            CheckBoxVal("Villainess", "villainess"),
            CheckBoxVal("War", "war"),
        ),
    )
