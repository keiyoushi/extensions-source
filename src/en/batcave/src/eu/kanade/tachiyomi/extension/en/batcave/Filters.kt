package eu.kanade.tachiyomi.extension.en.batcave

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface UrlPartFilter {
    fun addFilterToUrl(url: HttpUrl.Builder): Boolean
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
    override fun addFilterToUrl(url: HttpUrl.Builder): Boolean {
        val checked = state.filter { it.state }
            .also { if (it.isEmpty()) return false }
            .joinToString(",") { it.value.toString() }

        url.addPathSegments("$queryParameter=$checked/")
        return true
    }
}

class PublisherFilter(values: List<Pair<String, Int>>) :
    CheckBoxFilter("Publisher", "p", values)

class GenreFilter(values: List<Pair<String, Int>>) :
    CheckBoxFilter("Genre", "g", values)

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
    override fun addFilterToUrl(url: HttpUrl.Builder): Boolean {
        var applied = false
        val currentYear = yearFormat.format(Date()).toInt()
        if (state[0].state.isNotBlank()) {
            val from = try {
                state[0].state.toInt()
            } catch (_: NumberFormatException) {
                throw Exception("year must be number")
            }
            assert(from in 1929..currentYear) {
                "invalid start year (must be between 1929 and $currentYear)"
            }
            url.addPathSegments("y[from]=$from/")
            applied = true
        }
        if (state[1].state.isNotBlank()) {
            val to = try {
                state[1].state.toInt()
            } catch (_: NumberFormatException) {
                throw Exception("year must be number")
            }
            assert(to in 1929..currentYear) {
                "invalid start year (must be between 1929 and $currentYear)"
            }
            url.addPathSegments("y[to]=$to/")
            applied = true
        }
        return applied
    }
}

private val yearFormat = SimpleDateFormat("yyyy", Locale.ENGLISH)

class SortFilter(
    select: Selection = Selection(0, false),
) : Filter.Sort(
    "Sort",
    sorts.map { it.first }.toTypedArray(),
    select,
) {
    fun getSort() = sorts[state?.index ?: 0].second
    fun getDirection() = if (state?.ascending != false) {
        "asc"
    } else {
        "desc"
    }

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
