package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class NewTokiManga : NewTokiBase("NewToki Manga", "manhwa") {

    // TODO: Pagination relies on infinite scrolling API calls.
    // The `page` parameter is ignored until the AJAX request payload is implemented.
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhwa", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhwa/updates", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
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

        val url = "$baseUrl/manhwa$statusParam".toHttpUrl().newBuilder().apply {
            if (sortParam != "new") addQueryParameter("sort", sortParam)
            genreParam?.let { addQueryParameter("g", it) }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        Filter.Header(""), // added as a padding
    )
}
