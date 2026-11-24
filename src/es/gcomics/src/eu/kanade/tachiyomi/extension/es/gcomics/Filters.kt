package eu.kanade.tachiyomi.extension.es.gcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(TypeFilter())

class TypeFilter : Filter.Select<String>("Tipo", arrayOf("Todo", "Webtoons", "Comics")) {
    val webtoons get() = state != 2
    val comics get() = state != 1
}
