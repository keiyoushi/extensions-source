package eu.kanade.tachiyomi.extension.en.batcave

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

interface UrlPartFilter {
    fun addFilterToUrl(url: StringBuilder): Boolean
}

class CheckBoxItem(name: String, val value: Int) : Filter.CheckBox(name)

open class CheckBoxFilter(
    name: String,
    private val queryParameter: String,
    values: List<Pair<String, Int>>,
) : Filter.Group<CheckBoxItem>(
    name,
    values.map { CheckBoxItem(it.first, it.second) },
),
    UrlPartFilter {

    override fun addFilterToUrl(url: StringBuilder): Boolean {
        val checked = state.filter { it.state }
        if (checked.isEmpty()) return false

        val checkedStr = checked.joinToString(",") { it.value.toString() }
        url.append(queryParameter).append("=").append(checkedStr).append("/")
        return true
    }
}

class PublisherFilter(values: List<Pair<String, Int>>) : CheckBoxFilter("Publisher", "p", values)

class GenreFilter(values: List<Pair<String, Int>>) : CheckBoxFilter("Genre", "g", values)

class TextBox(name: String) : Filter.Text(name)

class YearFilter :
    Filter.Group<TextBox>(
        "Year of Issue",
        listOf(
            TextBox("from"),
            TextBox("to"),
        ),
    ),
    UrlPartFilter {

    override fun addFilterToUrl(url: StringBuilder): Boolean {
        var applied = false
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val fromStr = state[0].state
        if (fromStr.isNotBlank()) {
            val from = fromStr.toIntOrNull() ?: throw Exception("year must be number")
            require(from in 1929..currentYear) { "invalid start year (must be between 1929 and $currentYear)" }
            url.append("y[from]=").append(from).append("/")
            applied = true
        }

        val toStr = state[1].state
        if (toStr.isNotBlank()) {
            val to = toStr.toIntOrNull() ?: throw Exception("year must be number")
            require(to in 1929..currentYear) { "invalid end year (must be between 1929 and $currentYear)" }
            url.append("y[to]=").append(to).append("/")
            applied = true
        }

        return applied
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
    fun getDirection() = if (state?.ascending != false) "asc" else "desc"

    companion object {
        val POPULAR = FilterList(SortFilter(Selection(3, false)))
        val LATEST = FilterList(SortFilter(Selection(2, false)))
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
