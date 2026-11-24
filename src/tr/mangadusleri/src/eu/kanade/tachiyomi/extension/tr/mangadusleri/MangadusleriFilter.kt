package eu.kanade.tachiyomi.extension.tr.mangadusleri

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SortFilter(),
    GenresFilter(),
)

class SortFilter :
    UriPartFilter(
        "Sıralama",
        listOf(
            "En Çok Okunan" to "popular",
            "En Yeni" to "latest",
            "Biten Seriler" to "completed",
            "Eskiden Yeniye" to "oldest",
        ),
    )

class GenresFilter :
    UriPartFilter(
        "Türler",
        listOf("Tümü" to "") + listOf(
            "+18", "Aksiyon", "Cinsellik", "Drama", "Kavga",
            "Manhwa", "Romantik", "Smut", "Tarih", "Yaoi",
            "Yetişkin",
        ).map { it to it },
    )

open class UriPartFilter(
    displayName: String,
    private val vals: List<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = vals[state].second.ifEmpty { null }
}
