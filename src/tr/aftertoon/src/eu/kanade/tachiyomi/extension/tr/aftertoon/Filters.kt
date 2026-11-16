package eu.kanade.tachiyomi.extension.tr.aftertoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class SortFilter : SelectFilter("Sırala", SORTS)

class StatusFilter : SelectFilter("Durum", STATUSES)

class GenreFilter(genres: List<String>) : SelectFilter("Tür", listOf("Tüm Türler" to null) + genres.map { it to it })

fun getFilters(genres: List<String>?) = FilterList(
    listOfNotNull(
        SortFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter) ?: Filter.Header("Türleri yüklemek için 'Sıfırla'ya dokunun"),
    ),
)

private val SORTS = listOf(
    "Son Güncellemeler" to "updates",
    "En Yüksek Puan" to "rating",
    "Başlık A-Z" to "title",
)

private val STATUSES = listOf(
    "Tüm Durumlar" to null,
    "Devam ediyor" to "ongoing",
    "Tamamlandı" to "completed",
    "Ara Verdi" to "hiatus",
    "İptal Edildi" to "cancelled",
)
