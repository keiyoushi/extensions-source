package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter(defaultSort: String? = null) :
    UriSortFilter(
        name = "Sort By",
        sortParam = "sort",
        orderParam = "order",
        vals = arrayOf(
            "Latest Update" to "latest",
            "Popular" to "popular",
            "Rating" to "rating",
            "A-Z" to "title",
            "Newest" to "update",
        ),
        defaultValue = defaultSort,
        ascendingValue = "asc",
        descendingValue = "desc",
    )

open class UriSortFilter(
    name: String,
    private val sortParam: String,
    private val orderParam: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
    private val ascendingValue: String,
    private val descendingValue: String,
) : Filter.Sort(
    name,
    vals.map { it.first }.toTypedArray(),
    Selection(
        vals.indexOfFirst { it.second == defaultValue }
            .takeIf { it != -1 } ?: 0,
        ascending = false,
    ),
),
    UriFilter {

    override fun addToUri(builder: HttpUrl.Builder) {
        val state = state ?: return

        val selected = vals[state.index].second
        val order = if (state.ascending) ascendingValue else descendingValue

        builder.addQueryParameter(sortParam, selected)
        builder.addQueryParameter(orderParam, order)
    }
}
