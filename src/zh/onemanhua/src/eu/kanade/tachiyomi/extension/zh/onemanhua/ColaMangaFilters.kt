package eu.kanade.tachiyomi.extension.zh.onemanhua

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val uriPart = vals[state].second

        if (uriPart.isNotEmpty()) {
            builder.addQueryParameter(param, uriPart)
        }
    }
}

class SearchTypeFilter :
    UriPartFilter(
        "搜索类型",
        "type",
        arrayOf(
            "模糊" to "1",
            "精确" to "2",
        ),
    )
