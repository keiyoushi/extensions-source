package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortByFilter("Ordenar por", getSortProperties()),
    StatusFilter("Estado", getStatusList()),
)

private fun getSortProperties(): List<SortProperty> = listOf(
    SortProperty("Nombre", "name"),
    SortProperty("Vistas", "views"),
    SortProperty("Actualización", "updated_at"),
    SortProperty("Agregado", "created_at"),
)

private fun getStatusList() = arrayOf(
    Pair("Todos", 0),
    Pair("En curso", 1),
    Pair("En pausa", 2),
    Pair("Abandonado", 3),
    Pair("Finalizado", 4),
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
