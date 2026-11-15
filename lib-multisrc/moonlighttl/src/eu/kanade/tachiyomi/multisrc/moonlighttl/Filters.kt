package eu.kanade.tachiyomi.multisrc.moonlighttl

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.lib.i18n.Intl

fun getFilters(intl: Intl) = FilterList(
    SortByFilter(intl["sort_by"], getSortProperties(intl)),
    StatusFilter(intl["status"], getStatusList(intl)),
)

private fun getSortProperties(intl: Intl): List<SortProperty> = listOf(
    SortProperty(intl["sort_name"], "name"),
    SortProperty(intl["sort_views"], "views"),
    SortProperty(intl["sort_updated"], "updated_at"),
    SortProperty(intl["sort_created"], "created_at"),
)

private fun getStatusList(intl: Intl) = arrayOf(
    intl["status_all"] to 0,
    intl["status_ongoing"] to 1,
    intl["status_hiatus"] to 2,
    intl["status_dropped"] to 3,
    intl["status_completed"] to 4,
)

class SortProperty(val name: String, val value: String)

class SortByFilter(title: String, private val sortProperties: List<SortProperty>) :
    Filter.Sort(
        title,
        sortProperties.map { it.name }.toTypedArray(),
        Selection(2, ascending = false),
    ) {
    val selected: String
        get() = sortProperties[state!!.index].value
}

class StatusFilter(title: String, statusList: Array<Pair<String, Int>>) :
    UriPartFilter(
        title,
        statusList,
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, Int>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
