package eu.kanade.tachiyomi.extension.en.nuviatoon

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Locale

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "Hiatus"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ONGOING"
        2 -> "COMPLETED"
        3 -> "HIATUS"
        else -> null
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Popular", "Top rated", "Latest", "A-Z"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "rating"
        2 -> "newest"
        3 -> "alphabetical"
        else -> "popular"
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Type",
        arrayOf("All", "Manga", "Manhwa", "Manhua", "Webtoon"),
    ) {
    fun toUriPart(): String? = if (state == 0) {
        null
    } else {
        values[state].uppercase(Locale.ROOT)
    }
}

class RatingFilter :
    Filter.Select<String>(
        "Rating",
        arrayOf("Any", "4★ and up", "3★ and up"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "4"
        2 -> "3"
        else -> null
    }
}

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All",
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Fantasy",
            "Isekai",
            "Mature",
            "Mystery",
            "Psychological",
            "Historical",
            "Josei",
            "Revenge",
            "Romance",
            "Shoujo",
            "Slice Of Life",
        ),
    ) {
    fun toUriPart(): String? = if (state == 0) {
        null
    } else {
        values[state].lowercase(Locale.ROOT).replace(" ", "-")
    }
}
