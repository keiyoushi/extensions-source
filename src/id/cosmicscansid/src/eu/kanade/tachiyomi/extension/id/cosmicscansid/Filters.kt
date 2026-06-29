package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun getCosmicScansIDFilterList() = FilterList(
    OrderFilter(),
    StatusFilter(),
    TypeFilter(),
    ProjectFilter(),
    GenreFilter(GENRES),
)

internal open class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value: String get() = options[state].second
}

internal class OrderFilter :
    SelectFilter(
        "Urutkan",
        arrayOf(
            "Update" to "update",
            "Popular" to "popular",
            "A-Z" to "az",
            "Z-A" to "za",
            "Baru Ditambahkan" to "added",
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Semua" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
        ),
    )

internal class TypeFilter :
    SelectFilter(
        "Tipe",
        arrayOf(
            "Semua" to "",
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

internal class ProjectFilter :
    Filter.Select<String>(
        "Project",
        arrayOf(
            "Semua",
            "Project",
        ),
    )

internal class Genre(val genre: String) : Filter.CheckBox(genre)

internal class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

internal val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Martial Arts", "Romance",
    "School Life", "Shounen", "Supernatural", "System", "Thriller", "Murim",
).map { Genre(it) }
