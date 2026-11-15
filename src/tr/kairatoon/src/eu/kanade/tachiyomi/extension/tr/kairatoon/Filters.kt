package eu.kanade.tachiyomi.extension.tr.kairatoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected get() = options[state].second.takeIf(String::isNotEmpty)
}

class SortFilter : SelectFilter("Sırala", SORTS, 1)

class StatusFilter : SelectFilter("Durum", STATUSES)

class TypeFilter : SelectFilter("Tip", TYPES)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<Filter.CheckBox>("Tür", genres.map { object : Filter.CheckBox(it.name) {} }) {
    val checked get() = state.filter { it.state }.map { it.name }
}

fun getFilters(genres: List<GenreDto>?) = FilterList(
    listOfNotNull(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        genres?.let(::GenreFilter) ?: Filter.Header("Türleri yüklemek için 'Sıfırla'ya dokunun"),
    ),
)

private val SORTS = listOf(
    "En yeni bölümler" to "newest",
    "En popüler" to "popular",
    "En yüksek puan" to "top_rated",
    "En çok yorumlanan" to "most_commented",
    "Alfabetik" to "title",
)

private val STATUSES = listOf(
    "Tümü" to "",
    "Devam Ediyor" to "ongoing",
    "Tamamlandı" to "completed",
    "Mola" to "hiatus",
    "Sezon Finali" to "season_end",
)

private val TYPES = listOf(
    "Tümü" to "",
    "Manhwa" to "manhwa",
    "Manga" to "manga",
    "Manhua" to "manhua",
)
