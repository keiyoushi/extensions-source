package eu.kanade.tachiyomi.extension.ru.mangachan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Source
abstract class MangaChan : MultiChan() {

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val pageNum = page.coerceAtLeast(1)
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query)
                .addQueryParameter("search_start", pageNum.toString())
                .build()
                .toString()
            return GET(url, headers)
        }

        var genres = ""
        var statusParam = true
        var status = ""

        val filterList = filters.ifEmpty { getFilterList() }

        filterList.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.forEach { f ->
                        if (!f.isIgnored()) {
                            genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                        }
                    }
                }
                is OrderBy -> {
                    if (filter.state!!.ascending && filter.state!!.index == 0) {
                        statusParam = false
                    }
                }
                is Status -> status = arrayOf("", "all_done", "end", "ongoing", "new_ch")[filter.state]
                else -> {}
            }
        }

        val orderBy = filterList.firstInstanceOrNull<OrderBy>()
        val url = if (genres.isNotEmpty()) {
            val order = orderBy?.let {
                if (it.state!!.ascending) {
                    arrayOf("", "&n=favasc", "&n=abcdesc", "&n=chasc")[it.state!!.index]
                } else {
                    arrayOf("&n=dateasc", "&n=favdesc", "&n=abcasc", "&n=chdesc")[it.state!!.index]
                }
            } ?: ""

            if (statusParam) {
                "$baseUrl/tags/${genres.dropLast(1)}$order?offset=${20 * (pageNum - 1)}&status=$status"
            } else {
                "$baseUrl/tags/$status/${genres.dropLast(1)}/$order?offset=${20 * (pageNum - 1)}"
            }
        } else {
            val order = orderBy?.let {
                if (it.state!!.ascending) {
                    arrayOf("manga/new", "manga/new&n=favasc", "manga/new&n=abcdesc", "manga/new&n=chasc")[it.state!!.index]
                } else {
                    arrayOf("manga/new&n=dateasc", "mostfavorites", "catalog", "sortch")[it.state!!.index]
                }
            } ?: "manga/new"

            if (statusParam) {
                "$baseUrl/$order?offset=${20 * (pageNum - 1)}&status=$status"
            } else {
                "$baseUrl/$order/$status?offset=${20 * (pageNum - 1)}"
            }
        }

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        Status(),
        OrderBy(),
        GenreList(getGenreList()),
    )
}
