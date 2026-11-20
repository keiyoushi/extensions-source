package eu.kanade.tachiyomi.extension.tr.mangakutuphanem

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sıralama",
        arrayOf("Son güncellenen", "Yeni eklenen"),
    ) {
    val sort get() = if (state == 1) "added" else null
}

class SourceFilter(private val sources: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Kaynak",
        (listOf("Tümü") + sources.map { it.first }).toTypedArray(),
    ) {
    val selected get() = sources.getOrNull(state - 1)?.second
}
