package eu.kanade.tachiyomi.extension.tr.mangadusleri

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("NOT: Metinle arama yaparken filtreler yoksayılır!"),
    SortFilter(),
)

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            Pair("En Çok Okunan", "popular"),
            Pair("En Yeni", "latest"),
            Pair("Biten Seriler", "completed"),
            Pair("Eskiden Yeniye", "oldest"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = vals[state].second.ifEmpty { null }
}
