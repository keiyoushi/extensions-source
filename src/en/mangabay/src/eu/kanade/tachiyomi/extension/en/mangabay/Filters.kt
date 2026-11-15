package eu.kanade.tachiyomi.extension.en.mangabay

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

class GenreItem(name: String, val value: Int) : Filter.TriState(name)

class GenreFilter(values: List<Pair<String, Int>>) :
    Filter.Group<GenreItem>(
        "Genre",
        values.map { GenreItem(it.first, it.second) },
    ) {
    fun addToUrl(builder: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }
        val excluded = state.filter { it.isExcluded() }
        if (included.isNotEmpty()) {
            builder.addEncodedPathSegment("g=" + included.joinToString(",") { it.value.toString() })
        }
        if (excluded.isNotEmpty()) {
            builder.addEncodedPathSegment("exc_g=" + excluded.joinToString(",") { it.value.toString() })
        }
    }
}

class SortFilter(
    select: Selection = Selection(0, false),
) : Filter.Sort(
    "Sort",
    sorts.map { it.first }.toTypedArray(),
    select,
) {
    fun getSort() = sorts[state?.index ?: 0].second
    fun getDirection() = if (state?.ascending == true) "asc" else "desc"

    companion object {
        val POPULAR_STATE = Selection(3, false)
        val LATEST_STATE = Selection(1, false)
    }
}

private val sorts = listOf(
    "Default" to "",
    "Date" to "date",
    "Date of change" to "editdate",
    "Rating" to "rating",
    "Read" to "news_read",
    "Comments" to "comm_num",
    "Title" to "title",
)
