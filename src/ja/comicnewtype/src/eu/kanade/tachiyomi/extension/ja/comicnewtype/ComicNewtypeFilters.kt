package eu.kanade.tachiyomi.extension.ja.comicnewtype

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator

var genreList: List<Genre> = emptyList()

fun Document.parseGenres() {
    if (genreList.isNotEmpty()) return
    val container = select(Evaluator.Class("container__link-list--genre-btn")).lastOrNull() ?: return
    val items = container.children().ifEmpty { return }
    val list = ArrayList<Genre>(items.size + 1).apply { add(Genre("全て", null)) }
    genreList = items.mapTo(list) {
        val link = it.child(0)
        Genre(link.text(), link.attr("href"))
    }
}

val filterList: FilterList
    get() {
        val list = buildList(5) {
            if (genreList.isEmpty()) {
                add(Filter.Header("Press 'Reset' to attempt to show the genres"))
            } else {
                add(Filter.Header("Genre (ignored for text search)"))
                add(GenreFilter(genreList))
            }
            add(Filter.Separator())
            add(StatusFilter())
            add(SortFilter())
        }
        return FilterList(list)
    }

val FilterList.genrePath: String?
    get() {
        for (filter in this) {
            if (filter is GenreFilter) return filter.path
        }
        return null
    }

fun HttpUrl.Builder.addQueries(filters: FilterList): HttpUrl.Builder {
    for (filter in filters) {
        if (filter is QueryFilter) filter.addQueryTo(this)
    }
    return this
}

class Genre(val name: String, val path: String?)

class GenreFilter(private val list: List<Genre>) :
    Filter.Select<String>("Genre", list.map { it.name }.toTypedArray()) {
    val path get() = list[state].path
}

abstract class QueryFilter(name: String, values: Array<String>) :
    Filter.Select<String>(name, values) {
    abstract fun addQueryTo(builder: HttpUrl.Builder)
}

class StatusFilter : QueryFilter("Status", STATUS_VALUES) {
    override fun addQueryTo(builder: HttpUrl.Builder) {
        builder.addQueryParameter("refind_search", STATUS_QUERIES[state])
    }
}

private val STATUS_VALUES = arrayOf("全て", "連載中", "完結")
private val STATUS_QUERIES = arrayOf("all", "now", "fin")

class SortFilter : QueryFilter("Sort by", SORT_VALUES) {
    override fun addQueryTo(builder: HttpUrl.Builder) {
        if (state == 0) return
        builder.addQueryParameter("btn_sort", SORT_QUERIES[state])
    }
}

private val SORT_VALUES = arrayOf("更新順", "五十音順")
private val SORT_QUERIES = arrayOf("opendate", "alphabetical")
