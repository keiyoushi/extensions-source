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
            "A–Z" to "az",
            "Z–A" to "za",
            "Baru Ditambahkan" to "added",
            "Popular" to "popular",
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
            "Dropped" to "Dropped",
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
            "Webtoon" to "Webtoon",
        ),
    )

internal class ProjectFilter :
    SelectFilter(
        "Project",
        arrayOf(
            "Semua" to "",
            "Project Only" to "true",
        ),
    )

internal class Genre(name: String, val slug: String) : Filter.CheckBox(name)

internal class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

internal val GENRES = listOf(
    Genre("Action", "action"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Cultivation", "cultivation"),
    Genre("Delinquent", "delinquent"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Murim", "murim"),
    Genre("Mystery", "mystery"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Returner", "returner"),
    Genre("Revenge", "revenge"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shounen", "shounen"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("System", "system"),
    Genre("Thriller", "thriller"),
    Genre("Tragedy", "tragedy"),
)
