package eu.kanade.tachiyomi.extension.id.pramramadhan

import eu.kanade.tachiyomi.source.model.Filter

internal open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

internal class SortFilter :
    UriPartFilter(
        "Urutkan",
        arrayOf(
            Pair("Populer", "popular"),
            Pair("Terbaru", "newest"),
            Pair("Terlama", "oldest"),
            Pair("Judul A-Z", "title_asc"),
            Pair("Judul Z-A", "title_desc"),
        ),
    )

internal class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Semua", ""),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Magic", "Magic"),
            Pair("Romance", "Romance"),
            Pair("School", "School"),
            Pair("Slice of Life", "Slice of Life"),
        ),
    )

internal class FormatFilter :
    UriPartFilter(
        "Format",
        arrayOf(
            Pair("Semua", ""),
            Pair("Light Novel", "Light Novel"),
            Pair("Manga", "Manga"),
            Pair("Web Novel", "Web Novel"),
        ),
    )

internal class ProjectFilter :
    UriPartFilter(
        "Project",
        arrayOf(
            Pair("Semua", ""),
            Pair("Continued", "continued"),
            Pair("Completed", "completed"),
            Pair("Dropped", "dropped"),
        ),
    )

internal class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

internal class AuthorFilter : Filter.Text("Author")
internal class ArtistFilter : Filter.Text("Artist")
