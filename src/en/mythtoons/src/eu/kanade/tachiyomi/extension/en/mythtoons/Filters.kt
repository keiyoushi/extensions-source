package eu.kanade.tachiyomi.extension.en.mythtoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<String>?): FilterList = FilterList(
    listOfNotNull(
        StatusFilter(),
        genres?.let(::GenreFilter),
    ),
)

class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed", "Hiatus")) {
    val value get() = values[state].lowercase().takeIf { state > 0 }
}

class GenreFilter(genres: List<String>) : Filter.Select<String>("Genre", (listOf("All") + genres).toTypedArray()) {
    val value get() = values[state].takeIf { state > 0 }
}
