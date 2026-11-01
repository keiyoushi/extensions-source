package eu.kanade.tachiyomi.extension.ja.rawsakura

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

open class UriPartFilter(
    name: String,
    private val query: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, vals[state].second)
    }
}

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sort by",
        "sort",
        arrayOf(
            "Recently updated" to "",
            "Most viewed" to "most_viewed",
            "Most viewed today" to "most_viewed_today",
        ),
        state = 1,
    )
