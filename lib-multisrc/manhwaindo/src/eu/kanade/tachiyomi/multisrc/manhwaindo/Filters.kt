package eu.kanade.tachiyomi.multisrc.manhwaindo

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class SortFilter(state: Int = 0) :
    UriPartFilter(
        "Urutkan",
        listOf(
            "Update Terbaru" to "modified",
            "Paling Banyak Dibaca" to "views",
            "Terbaru Ditambahkan" to "date",
            "Judul A–Z" to "title",
        ),
        state,
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        listOf(
            "Semua" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        listOf(
            "Semua" to "",
            "Manga" to "Manga",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
            "Comic" to "Comic",
            "Novel" to "Novel",
        ),
    )

class AuthorFilter : Filter.Text("Penulis")

class ArtistFilter : Filter.Text("Artist")

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<Genre>("Genre", genres.map { Genre(it.name, it.value) })
