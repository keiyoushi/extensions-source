package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.jsoup.nodes.Document

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

class GenreFilter(genres: List<Pair<String, String>>) {
    private val values = genres.map { it.first }.toTypedArray()
    private val queries = genres.map { it.second }.toTypedArray()

    private val apiQueries get() = queries.run {
        Array(size) { i -> "type[tags]=" + this[i] }.apply { this[0] = "" }
    }

    private val webQueries get() = queries.run {
        Array(size) { i -> "tags/" + this[i] }.apply { this[0] = "" }
    }

    val filter get() = MCCMSFilter(Intl.genreApi, values, apiQueries, isTypeQuery = true)
    val webFilter get() = MCCMSFilter(Intl.genreWeb, values, webQueries, isTypeQuery = true)
}

internal fun parseGenres(document: Document): List<Pair<String, String>> {
    val box = document.selectFirst(".cate-selector, .cy_list_l, .ticai, .stui-screen__list")
        ?: throw Exception("Genre list not found")
    val genres = box.select("a[href*=/tags/]")
    if (genres.isEmpty()) return emptyList()
    return buildList(genres.size + 1) {
        add(Pair(Intl.all, ""))
        genres.mapTo(this) {
            val tagId = it.attr("href").substringAfterLast('/')
            Pair(it.text(), tagId)
        }
    }
}

internal fun getFilters(genres: List<Pair<String, String>>?): FilterList {
    val list = buildList(4) {
        if (Intl.lang == "zh") add(StatusFilter())
        add(SortFilter())
        if (!genres.isNullOrEmpty()) {
            add(Filter.Separator())
            add(GenreFilter(genres).filter)
        }
    }
    return FilterList(list)
}

internal fun getWebFilters(genres: List<Pair<String, String>>?): FilterList {
    val list = buildList(4) {
        add(Filter.Header(Intl.categoryWeb))
        add(WebStatusFilter())
        add(WebSortFilter())
        if (!genres.isNullOrEmpty()) add(GenreFilter(genres).webFilter)
    }
    return FilterList(list)
}
