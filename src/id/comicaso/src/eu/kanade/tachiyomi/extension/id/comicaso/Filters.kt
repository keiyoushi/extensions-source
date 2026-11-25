package eu.kanade.tachiyomi.extension.id.comicaso

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Jenis",
        listOf("Semua jenis" to null, "Manga" to "Manga", "Manhwa" to "Manhwa", "Manhua" to "Manhua"),
    )

// The site's own form sends the Indonesian labels, which the server matches to nothing
class StatusFilter :
    SelectFilter(
        "Status",
        listOf("Semua status" to null, "Berjalan" to "Ongoing", "Tamat" to "Completed"),
    )

class SortFilter :
    SelectFilter(
        "Urutkan",
        listOf("Terakhir diperbarui" to "updated", "Terbaru ditambahkan" to "new"),
    )

class GenreFilter(genres: List<GenreDto>) : SelectFilter("Genre", listOf("Semua genre" to null) + genres.map { it.name to it.value })
