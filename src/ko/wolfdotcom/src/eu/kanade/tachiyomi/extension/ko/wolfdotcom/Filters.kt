package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.net.URLEncoder

interface UrlPartFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

@Serializable
class FilterRow(
    val param: String,
    val options: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

class RowFilter(
    private val row: FilterRow,
) : Filter.Select<String>(
    ROW_NAMES[row.param] ?: row.param,
    row.options.map { it.name }.toTypedArray(),
),
    UrlPartFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = row.options[state].value
        if (value.isNotEmpty()) {
            // the site only understands EUC-KR encoded query values
            url.addEncodedQueryParameter(row.param, URLEncoder.encode(value, "EUC-KR"))
        }
    }

    companion object {
        private val ROW_NAMES = mapOf(
            "t1" to "요일",
            "t2" to "분류",
            "t3" to "장르",
        )
    }
}

class StatusFilter : Filter.Select<String>("상태", arrayOf("연재", "완결"))

class SortFilter(
    private val options: List<Pair<String, String>>,
    default: String = options.first().second,
) : Filter.Select<String>(
    "정렬 기준",
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == default },
),
    UrlPartFilter {

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("o", options[state].second)
    }
}
