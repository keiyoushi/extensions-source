package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class NewTokiWebtoon : NewTokiBase("NewToki Webtoon", "webtoon") {

    // TODO: Pagination relies on infinite scrolling API calls.
    // The `page` parameter is ignored until the AJAX request payload is implemented.
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ing?sort=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ing", headers)

    // Webtoons use the standard card grid for latest updates, not the upd-grid
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                addQueryParameter("kind", "webtoon")
            }.build()
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstanceOrNull<WtSortFilter>()
        val statusFilter = filters.firstInstanceOrNull<WtStatusFilter>()
        val catFilter = filters.firstInstanceOrNull<WtCategoryFilter>()
        val dayFilter = filters.firstInstanceOrNull<WtDayFilter>()
        val genreFilter = filters.firstInstanceOrNull<WtGenreFilter>()

        val sortParam = sortFilter?.let { sortList[it.state].value } ?: sortList[0].value
        val statusParam = statusFilter?.let { wtStatusList[it.state].value } ?: wtStatusList[0].value
        val catParam = catFilter?.let { wtCatList[it.state].value } ?: ""
        val dayParam = dayFilter?.let { wtDayList[it.state].value } ?: ""
        val tagParam = buildWtGenreParam(genreFilter)

        val url = "$baseUrl/$statusParam".toHttpUrl().newBuilder().apply {
            if (sortParam != "new") addQueryParameter("sort", sortParam)

            // Site ignores Category and Day filters on the /end page
            if (statusParam != "end") {
                if (catParam.isNotEmpty()) addQueryParameter("cat", catParam)
                if (dayParam.isNotEmpty()) addQueryParameter("day", dayParam)
            }

            if (!tagParam.isNullOrEmpty()) addQueryParameter("tag", tagParam)
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        WtSortFilter(),
        WtStatusFilter(),
        WtCategoryFilter(),
        WtDayFilter(),
        WtGenreFilter(),
        Filter.Header(""), // added as a padding
    )
}
