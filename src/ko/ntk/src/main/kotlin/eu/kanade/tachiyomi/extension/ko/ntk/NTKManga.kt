package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class NTKManga : NTKBase("NTK Manga", "manhwa") {

    // Popular: sorted by views via JSON API — supports infinite pagination via hasMore
    override fun popularMangaRequest(page: Int): Request {
        val url = "$rootUrl/api/manhwa-list".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", "ongoing")
            addQueryParameter("sort", "views")
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", PAGE_SIZE.toString())
            addQueryParameter("withTotal", "1")
        }.build()
        return GET(url, apiHeaders)
    }

    // Latest: fetches /manhwa/updates — all 200 entries are embedded in the RSC payload, parsed in NTKBase
    override fun latestUpdatesRequest(page: Int): Request = GET("$rootUrl/manhwa/updates", headers)

    // Search: text query uses HTML search endpoint; filters use JSON API with status/sort/genre params
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$rootUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                addQueryParameter("kind", "manhwa")
            }.build()
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val sortParam = sortFilter?.let { sortList[it.state].value } ?: sortList[0].value
        val statusParam = statusFilter?.let { statusList[it.state].value } ?: statusList[0].value
        val genreParam = buildGenreParam(genreFilter)

        // HTML URL uses "-end" suffix; API uses "end" as a direct param value
        val apiStatus = if (statusParam == "-end") "end" else "ongoing"

        val url = "$rootUrl/api/manhwa-list".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", apiStatus)
            if (sortParam != "new") addQueryParameter("sort", sortParam)
            genreParam?.let { addQueryParameter("g", it) }
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", PAGE_SIZE.toString())
            addQueryParameter("withTotal", "1")
        }.build()
        return GET(url, apiHeaders)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        Filter.Header(""),
    )
}
