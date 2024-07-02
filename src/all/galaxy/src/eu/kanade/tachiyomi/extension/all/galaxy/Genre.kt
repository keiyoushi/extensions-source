package eu.kanade.tachiyomi.extension.all.galaxy

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val queryParam: String,
) : Filter.Select<String>(
    name,
    buildList {
        add("")
        addAll(options.map { it.first })
    }.toTypedArray(),
) {
    fun addFilterParameter(url: HttpUrl.Builder) {
        if (state == 0) return

        url.addQueryParameter(queryParam, options[state - 1].second)
    }
}

class FilterData(
    val displayName: String,
    val options: List<Pair<String, String>>,
    val queryParameter: String,
)
