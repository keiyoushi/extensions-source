package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class NTKWebtoon : NTKBase("NTK Webtoon", "webtoon") {
    override val webViewPath = "ing"

    override fun popularMangaRequest(page: Int): Request {
        val url = "$rootUrl/api/works".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", "ongoing")
            addQueryParameter("sort", "views")
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", PAGE_SIZE.toString())
            addQueryParameter("withTotal", "1")
        }.build()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$rootUrl/api/works".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", "ongoing")
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", PAGE_SIZE.toString())
            addQueryParameter("withTotal", "1")
        }.build()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$rootUrl/search".toHttpUrl().newBuilder().apply {
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

        val apiStatus = if (statusParam == "end") "end" else "ongoing"

        val url = "$rootUrl/api/works".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", apiStatus)
            if (sortParam != "new") addQueryParameter("sort", sortParam)
            if (statusParam != "end") {
                if (catParam.isNotEmpty()) addQueryParameter("cat", catParam)
                if (dayParam.isNotEmpty()) addQueryParameter("day", dayParam)
            }
            if (!tagParam.isNullOrEmpty()) addQueryParameter("tag", tagParam)
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", PAGE_SIZE.toString())
            addQueryParameter("withTotal", "1")
        }.build()
        return GET(url, apiHeaders)
    }

    override fun getFilterList() = FilterList(
        WtSortFilter(),
        WtStatusFilter(),
        WtCategoryFilter(),
        WtDayFilter(),
        WtGenreFilter(),
        Filter.Header(""),
    )
}
