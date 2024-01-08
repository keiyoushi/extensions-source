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

class SortFilter : MCCMSFilter("排序", SORT_NAMES, SORT_QUERIES)
class WebSortFilter : MCCMSFilter("排序", SORT_NAMES, SORT_QUERIES_WEB)

private val SORT_NAMES = arrayOf("热门人气", "更新时间", "评分")
private val SORT_QUERIES = arrayOf("order=hits", "order=addtime", "order=score")
private val SORT_QUERIES_WEB = arrayOf("order/hits", "order/addtime", "order/score")

class StatusFilter : MCCMSFilter("进度", STATUS_NAMES, STATUS_QUERIES)
class WebStatusFilter : MCCMSFilter("进度", STATUS_NAMES, STATUS_QUERIES_WEB)

private val STATUS_NAMES = arrayOf("全部", "连载", "完结")
private val STATUS_QUERIES = arrayOf("", "serialize=连载", "serialize=完结")
private val STATUS_QUERIES_WEB = arrayOf("", "finish/1", "finish/2")

class GenreFilter(private val values: Array<String>, private val queries: Array<String>) {

    private val apiQueries get() = queries.run {
        Array(size) { i -> "type[tags]=" + this[i] }
    }

    private val webQueries get() = queries.run {
        Array(size) { i -> "tags/" + this[i] }
    }

    val filter get() = MCCMSFilter("标签(搜索文本时无效)", values, apiQueries, isTypeQuery = true)
    val webFilter get() = MCCMSFilter("标签", values, webQueries, isTypeQuery = true)
}

class GenreData(hasCategoryPage: Boolean) {
    var status = if (hasCategoryPage) NOT_FETCHED else NO_DATA
    lateinit var genreFilter: GenreFilter

    companion object {
        const val NOT_FETCHED = 0
        const val FETCHING = 1
        const val FETCHED = 2
        const val NO_DATA = 3
    }
}

internal fun parseGenres(document: Document, genreData: GenreData) {
    val genres = document.select("a[href^=/category/tags/]")
    if (genres.isEmpty()) {
        genreData.status = GenreData.NO_DATA
        return
    }
    val result = buildList(genres.size + 1) {
        add(Pair("全部", ""))
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
        add(StatusFilter())
        add(SortFilter())
        if (genreData.status == GenreData.NO_DATA) return@buildList
        add(Filter.Separator())
        if (genreData.status == GenreData.FETCHED) {
            add(genreData.genreFilter.filter)
        } else {
            add(Filter.Header("点击“重置”尝试刷新标签分类"))
        }
    }
    return FilterList(list)
}

internal fun getWebFilters(genreData: GenreData): FilterList {
    val list = buildList(4) {
        add(Filter.Header("分类筛选（搜索时无效）"))
        add(WebStatusFilter())
        add(WebSortFilter())
        when (genreData.status) {
            GenreData.NO_DATA -> return@buildList
            GenreData.FETCHED -> add(genreData.genreFilter.webFilter)
            else -> add(Filter.Header("点击“重置”尝试刷新标签分类"))
        }
    }
    return FilterList(list)
}
