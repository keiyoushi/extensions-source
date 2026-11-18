package eu.kanade.tachiyomi.extension.id.kaguya

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreDto>?) = FilterList(
    listOfNotNull(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter),
    ),
)

open class QueryFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second.ifEmpty { null }
}

class SortFilter :
    QueryFilter(
        "Urutkan",
        "sort",
        listOf(
            "Terbaru" to "latest",
            "Terlama" to "oldest",
            "Populer" to "popular",
            "A-Z" to "az",
            "Z-A" to "za",
            "Bookmark" to "bookmarks",
            "Tahun" to "year",
            "Jumlah Chapter" to "chapters",
        ),
        state = 2,
    )

class TypeFilter :
    QueryFilter(
        "Tipe",
        "type",
        listOf(
            "Semua" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
        ),
    )

class StatusFilter :
    QueryFilter(
        "Status",
        "status",
        listOf(
            "Semua" to "",
            "Ongoing" to "ONGOING",
            "End Season" to "END_SEASON",
            "Tamat" to "END",
            "Drop" to "DROP",
        ),
    )

class GenreFilter(genres: List<GenreDto>) :
    QueryFilter(
        "Genre",
        "genre",
        listOf("Semua" to "") + genres.map { it.name to it.slug },
    )
