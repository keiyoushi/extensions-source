package eu.kanade.tachiyomi.multisrc.aesir

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

interface QueryFilter {
    val param: String
    val value: String?
}

open class SelectFilter(
    name: String,
    override val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state),
    QueryFilter {
    override val value get() = options[state].second.ifEmpty { null }
}

class SortFilter :
    SelectFilter(
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
    SelectFilter(
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
    SelectFilter(
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

class GenreOption(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) :
    Filter.Group<GenreOption>("Genre", genres.map { GenreOption(it.name, it.slug) }),
    QueryFilter {
    override val param = "genre"
    override val value get() = state.filter { it.state }.joinToString(",") { it.slug }.ifEmpty { null }
}
