package eu.kanade.tachiyomi.extension.en.nuviatoon

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "Hiatus"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        3 -> "hiatus"
        else -> null
    }
}

class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Popular", "Latest", "A-Z"),
        Selection(0, false), // Defaults to "Popular" descending
    ) {
    fun toUriPart(): String = when (state?.index) {
        1 -> "created_at"
        2 -> "title"
        else -> "views"
    }

    fun toDirPart(): String = if (state?.ascending == true) "asc" else "desc"
}

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All",
            "Action",
            "Romance",
            "Fantasy",
            "Comedy",
            "Adventure",
            "Drama",
            "Mystery",
            "Supernatural",
            "School Life",
            "Shounen",
            "Shoujo",
            "Magic",
        ),
    ) {
    fun toUriPart(): String? = if (state == 0) null else values[state]
}
