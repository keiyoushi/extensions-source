package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class FilterData(
    val type: String,
    private val typeDisplayName: String? = null,
    val value: String?,
    private val valueDisplayName: String,
) {
    override fun toString(): String {
        return "$typeDisplayName: $valueDisplayName"
    }
}

class SearchFilter(
    private val options: List<FilterData>,
) : Filter.Select<String>(
    "필터",
    options.map { it.toString() }.toTypedArray(),
),
    UrlPartFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val selected = options[state]
        url.addQueryParameter("type1", selected.type)
        selected.value?.let {
            url.addQueryParameter("type2", it)
        }
    }
}

class SortFilter(default: Int = 0) :
    Filter.Select<String>(
        "정렬 기준",
        options.map { it.first }.toTypedArray(),
        default,
    ),
    UrlPartFilter {

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("o", options[state].second)
    }

    companion object {
        private val options = listOf(
            "최신순" to "n",
            "인기순" to "f",
        )
    }
}

val POPULAR = FilterList(SortFilter(1))
val LATEST = FilterList(SortFilter(0))
