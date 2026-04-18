package eu.kanade.tachiyomi.extension.id.doujindesuunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter(
    state: String? = sortValues[0].second,
) : Filter.Select<String>(
    name = "Urutkan",
    values = sortValues.map { it.first }.toTypedArray(),
    state = sortValues.indexOfFirst { it.second == state }.takeIf { it != -1 } ?: 0,
) {
    val sort get() = sortValues[state].second

    companion object {
        val popular get() = FilterList(SortFilter("popular"))
        val latest get() = FilterList(SortFilter("latest"))
    }
}

private val sortValues = listOf(
    "Semua" to null,
    "Update" to "latest",
    "Populer" to "popular",
    "A-Z" to "az",
    "Z-A" to "za",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = statusValues.map { it.first }.toTypedArray(),
    ) {
    val status get() = statusValues[state].second
}

private val statusValues = listOf(
    "Semua" to null,
    "Ongoing" to "publishing",
    "Completed" to "finished",
)

class TypeFilter :
    Filter.Select<String>(
        name = "Tipe",
        values = typeValues.map { it.first }.toTypedArray(),
    ) {
    val type get() = typeValues[state].second
}

private val typeValues = listOf(
    "Semua" to null,
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Doujinshi" to "doujinshi",
)

class GenreFilter(
    private val genres: List<FilterData>,
) : Filter.Select<String>(
    "Genre",
    arrayOf("Semua", *genres.map { it.name }.toTypedArray()),
) {
    val genre get() = if (state == 0) null else genres[state - 1].name
}
