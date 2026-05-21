package eu.kanade.tachiyomi.extension.en.mangabay

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addToUrl(builder: HttpUrl.Builder): Boolean
}

class CheckBoxItem(name: String, val value: Int) : Filter.CheckBox(name)

class GenreFilter(values: List<Pair<String, Int>>) :
    Filter.Group<CheckBoxItem>(
        "Genre",
        values.map { CheckBoxItem(it.first, it.second) },
    ),
    UrlPartFilter {
    override fun addToUrl(builder: HttpUrl.Builder): Boolean {
        val checked = state.filter { it.state }
        if (checked.isEmpty()) return false
        builder.addEncodedPathSegment("g=" + checked.joinToString(",") { it.value.toString() })
        return true
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
