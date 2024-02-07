package eu.kanade.tachiyomi.extension.all.comikey

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getComikeyFilters(intl: Intl) = FilterList(
    Filter.Header(intl["search_use_two_characters"]),
    Filter.Separator(),
    SortFilter(intl["sort_by"], getSortOptions(intl)),
    TypeFilter(intl["filter_by"], getTypeOptions(intl)),
)

fun getSortOptions(intl: Intl) = arrayOf(
    intl["sort_last_updated"],
    intl["sort_name"],
    intl["sort_popularity"],
    intl["sort_chapter_count"],
)

fun getTypeOptions(intl: Intl) = arrayOf(
    intl["all"],
    intl["manga"],
    intl["webtoon"],
    intl["new"],
    intl["complete"],
    intl["exclusive"],
    intl["simulpub"],
)

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter(name: String, values: Array<String>) :
    Filter.Sort(name, values, Selection(2, false)),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val state = this.state ?: return
        val value = buildString {
            if (!state.ascending) {
                append("-")
            }

            when (state.index) {
                0 -> append("updated")
                1 -> append("name")
                2 -> append("views")
                3 -> append("chapters")
            }
        }

        builder.addQueryParameter("order", value)
    }
}

class TypeFilter(name: String, values: Array<String>) :
    Filter.Select<String>(name, values),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state == 0) {
            return
        }

        builder.addQueryParameter("filter", values[state].lowercase())
    }
}
