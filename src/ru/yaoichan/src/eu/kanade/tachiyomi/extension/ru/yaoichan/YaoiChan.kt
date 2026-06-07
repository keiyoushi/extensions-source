package eu.kanade.tachiyomi.extension.ru.yaoichan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class YaoiChan : MultiChan("YaoiChan", "https://yaoi-chan.me", "ru") {

    override val id: Long = 2466512768990363955

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query)
                .addQueryParameter("search_start", page.toString())
                .build()
                .toString()
        } else {
            var genres = ""
            var order = ""
            var statusParam = true
            var status = ""
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
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

            if (genres.isNotEmpty()) {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("", "&n=favasc", "&n=abcdesc", "&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("&n=dateasc", "&n=favdesc", "&n=abcasc", "&n=chdesc")[filter.state!!.index]
                            }
                        }

                        else -> {}
                    }
                }
                if (statusParam) {
                    "$baseUrl/tags/${genres.dropLast(1)}$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/tags/$status/${genres.dropLast(1)}/$order?offset=${20 * (page - 1)}"
                }
            } else {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("manga/new", "manga/new&n=favasc", "manga/new&n=abcdesc", "manga/new&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("manga/new&n=dateasc", "mostfavorites", "catalog", "sortch")[filter.state!!.index]
                            }
                        }

                        else -> {}
                    }
                }
                if (statusParam) {
                    "$baseUrl/$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/$order/$status?offset=${20 * (page - 1)}"
                }
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
