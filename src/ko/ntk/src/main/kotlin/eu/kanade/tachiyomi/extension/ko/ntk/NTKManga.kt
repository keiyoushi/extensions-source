package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// class NTKManga : NTKBase("NTK Manga", "manhwa") {
//
//    // TODO: Pagination relies on infinite scrolling API calls.
//    // The `page` parameter is ignored until the AJAX request payload is implemented.
//    override fun popularMangaRequest(page: Int): Request = GET("$rootUrl/manhwa", headers)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$rootUrl/manhwa/updates", headers)
//
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        if (query.isNotEmpty()) {
//            val url = "$rootUrl/search".toHttpUrl().newBuilder().apply {
//                addQueryParameter("q", query)
//                addQueryParameter("kind", "manhwa")
//            }.build()
//            return GET(url, headers)
//        }
//
//        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
//        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
//        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
//
//        val sortParam = sortFilter?.let { sortList[it.state].value } ?: sortList[0].value
//        val statusParam = statusFilter?.let { statusList[it.state].value } ?: statusList[0].value
//        val genreParam = buildGenreParam(genreFilter)
//
//        val url = "$rootUrl/manhwa$statusParam".toHttpUrl().newBuilder().apply {
//            if (sortParam != "new") addQueryParameter("sort", sortParam)
//            genreParam?.let { addQueryParameter("g", it) }
//        }.build()
//
//        return GET(url, headers)
//    }
//
//    override fun getFilterList() = FilterList(
//        SortFilter(),
//        StatusFilter(),
//        GenreFilter(),
//        Filter.Header(""),
//    )
// }

class NTKManga : NTKBase("NTK Manga", "manhwa") {

    // TODO: Pagination relies on the JSON API.
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

    override fun latestUpdatesRequest(page: Int): Request = GET("$rootUrl/manhwa/updates", headers)

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

        // "-end" is the HTML URL suffix; the API uses "end" directly
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
