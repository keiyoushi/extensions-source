package eu.kanade.tachiyomi.extension.tr.xiaoaitoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Select<String>("Sırala", SORTS.map { it.first }.toTypedArray(), 3) {
    val value: String get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Son Güncellenen" to "latest",
            "Yeni" to "new-manga",
            "Popüler" to "trending",
            "En Çok Okunan" to "views",
            "A-Z" to "alphabet",
        )
    }
}

fun getFilters() = FilterList(
    Filter.Header("Arama yaparken sıralama kullanılmaz"),
    SortFilter(),
)
