package eu.kanade.tachiyomi.extension.en.fairyscans

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf("Latest update", "Popularity", "Rating", "A-Z", "Newest"),
        Selection(0, false),
    )

class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed", "Hiatus", "Dropped"))

class TypeFilter : Filter.Select<String>("Type", arrayOf("All", "Novel", "Manga", "Manhwa", "Manhua", "Mangatoon"))

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All genres", "Action", "Adaptation", "Adventure", "Bloody", "Comedy", "Comic", "Demons", "Drama",
            "Fantasy", "Historical", "Horror", "Isekai", "Josei", "Kids", "Magic", "Manga", "Manhua", "Manhwa",
            "Mystery", "Novel", "Office workers", "One shot", "Psychological", "Reincarnation", "Revenge",
            "Romance", "Royal family", "School Life", "Shoujo", "Shounen", "Slice of Life", "Superhero",
            "Supernatural", "Time travel", "Tragedy", "Transmigration", "Villainess", "Webtoons",
        ),
    )

val SORT_VALUES = arrayOf("latest", "popular", "rating", "az", "newest")
val STATUS_VALUES = arrayOf("all", "ongoing", "completed", "hiatus", "dropped")
val TYPE_VALUES = arrayOf("all", "novel", "manga", "manhwa", "manhua", "mangatoon")
val GENRE_VALUES = arrayOf(
    "", "action", "adaptation", "adventure", "bloody", "comedy", "comic", "demons", "drama", "fantasy",
    "historical", "horror", "isekai", "josei", "kids", "magic", "manga", "manhua", "manhwa", "mystery",
    "novel", "office-workers", "one-shot", "psychological", "reincarnation", "revenge", "romance",
    "royal-family", "school-life", "shoujo", "shounen", "slice-of-life", "superhero", "supernatural",
    "time-travel", "tragedy", "transmigration", "villainess", "webtoons",
)
