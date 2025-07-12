package eu.kanade.tachiyomi.multisrc.mccms

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import kotlin.concurrent.thread

open class MCCMSFilter(
    name: String,
    values: Array<String>,
    private val queries: Array<String>,
    val isTypeQuery: Boolean = false,
) : Filter.Select<String>(name, values) {
    val query get() = queries[state]
}

class SortFilter : MCCMSFilter(Intl.sort, SORT_NAMES, SORT_QUERIES)
class WebSortFilter : MCCMSFilter(Intl.sort, SORT_NAMES, SORT_QUERIES_WEB)

private val SORT_NAMES get() = arrayOf(Intl.popular, Intl.latest, Intl.score)
private val SORT_QUERIES get() = arrayOf("order=hits", "order=addtime", "order=score")
private val SORT_QUERIES_WEB get() = arrayOf("order/hits", "order/addtime", "order/score")

class StatusFilter : MCCMSFilter(Intl.status, STATUS_NAMES, STATUS_QUERIES)
class WebStatusFilter : MCCMSFilter(Intl.status, STATUS_NAMES, STATUS_QUERIES_WEB)

private val STATUS_NAMES get() = arrayOf(Intl.all, Intl.ongoing, Intl.completed)
private val STATUS_QUERIES get() = arrayOf("", "serialize=连载", "serialize=完结")
private val STATUS_QUERIES_WEB get() = arrayOf("", "finish/1", "finish/2")

class GenreFilter(private val values: Array<String>, private val queries: Array<String>) {
    private val apiQueries get() = queries.run {
        Array(size) { i -> "type[tags]=" + this[i] }.apply { this[0] = "" }
    }

    private val webQueries get() = queries.run {
        Array(size) { i -> "tags/" + this[i] }.apply { this[0] = "" }
    }

    val filter get() = MCCMSFilter(Intl.genreApi, values, apiQueries, isTypeQuery = true)
    val webFilter get() = MCCMSFilter(Intl.genreWeb, values, webQueries, isTypeQuery = true)
}

class GenreData(hasCategoryPage: Boolean) {
    var status = if (hasCategoryPage) NOT_FETCHED else NO_DATA
    lateinit var genreFilter: GenreFilter

    fun fetchGenres(source: HttpSource) {
        if (status != NOT_FETCHED) return
        status = FETCHING
        thread {
            try {
                val request = when (source) {
                    // Web sources parse listings whenever possible. They call this function for mobile pages.
                    is MCCMSWeb -> GET("${source.baseUrl.mobileUrl()}/category/", source.headers)
                    else -> GET("${source.baseUrl}/category/", pcHeaders)
                }
                val response = source.client.newCall(request).execute()
                parseGenres(response.asJsoup(), this)
            } catch (e: Exception) {
                status = NOT_FETCHED
                Log.e("MCCMS/${source.name}", "failed to fetch genres", e)
            }
        }
    }

    companion object {
        const val NOT_FETCHED = 0
        const val FETCHING = 1
        const val FETCHED = 2
        const val NO_DATA = 3
    }
}

internal fun parseGenres(document: Document, genreData: GenreData) {
    if (genreData.status == GenreData.FETCHED || genreData.status == GenreData.NO_DATA) return
    val box = document.selectFirst(".cate-selector, .cy_list_l, .ticai, .stui-screen__list")
    if (box == null || "/tags/" in document.location()) {
        genreData.status = GenreData.NOT_FETCHED
        return
    }
    val genres = box.select("a[href*=/tags/]")
    if (genres.isEmpty()) {
        genreData.status = GenreData.NO_DATA
        return
    }
    val result = buildList(genres.size + 1) {
        add(Pair(Intl.all, ""))
        genres.mapTo(this) {
            val tagId = it.attr("href").substringAfterLast('/')
            Pair(it.text(), tagId)
        }
    }
    genreData.genreFilter = GenreFilter(
        values = result.map { it.first }.toTypedArray(),
        queries = result.map { it.second }.toTypedArray(),
    )
    genreData.status = GenreData.FETCHED
}

internal fun getFilters(genreData: GenreData): FilterList {
    val list = buildList(4) {
        if (Intl.lang == "zh") add(StatusFilter())
        add(SortFilter())
        if (genreData.status == GenreData.NO_DATA) return@buildList
        add(Filter.Separator())
        if (genreData.status == GenreData.FETCHED) {
            add(genreData.genreFilter.filter)
        } else {
            add(Filter.Header(Intl.tapReset))
        }
    }
    return FilterList(list)
}

internal fun getWebFilters(genreData: GenreData): FilterList {
    val list = buildList(4) {
        add(Filter.Header(Intl.categoryWeb))
        add(WebStatusFilter())
        add(WebSortFilter())
        when (genreData.status) {
            GenreData.NO_DATA -> return@buildList
            GenreData.FETCHED -> add(genreData.genreFilter.webFilter)
            else -> add(Filter.Header(Intl.tapReset))
        }
    }
    return FilterList(list)
}
