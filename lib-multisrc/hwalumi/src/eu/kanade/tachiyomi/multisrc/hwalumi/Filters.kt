package eu.kanade.tachiyomi.multisrc.hwalumi

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter :
    Filter.Select<String>(
        "Urutkan",
        arrayOf("Terbaru", "Populer", "Rating", "A - Z"),
    ) {
    fun getValue(): String = when (state) {
        0 -> "latest"
        1 -> "popular"
        2 -> "rating"
        3 -> "az"
        else -> "latest"
    }
}

internal class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Ongoing", "Completed", "Hiatus"),
    ) {
    fun getValue(): String = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        3 -> "hiatus"
        else -> ""
    }
}

internal class TypeFilter :
    Filter.Select<String>(
        "Tipe",
        arrayOf("Semua", "Manga", "Manhwa", "Manhua"),
    ) {
    fun getValue(): String = when (state) {
        1 -> "manga"
        2 -> "manhwa"
        3 -> "manhua"
        else -> ""
    }
}

internal class Genre(val name: String, val id: String)
internal class GenreCheckBox(val genre: Genre) : Filter.CheckBox(genre.name)
internal class GenreListFilter(genres: List<Genre>) : Filter.Group<GenreCheckBox>("Genre", genres.map(::GenreCheckBox)) {
    fun getIncluded(): String = state.filter { it.state }.joinToString(",") { it.genre.id }
}
