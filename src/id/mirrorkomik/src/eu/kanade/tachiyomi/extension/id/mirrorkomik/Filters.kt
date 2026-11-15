package eu.kanade.tachiyomi.extension.id.mirrorkomik

import eu.kanade.tachiyomi.source.model.Filter

private val TYPES = arrayOf(
    "Semua" to "Komik",
    "Manga" to "Manga",
    "Manhwa" to "Manhwa",
    "Manhua" to "Manhua",
)

class TypeFilter : Filter.Select<String>("Tipe", TYPES.map { it.first }.toTypedArray()) {
    val value get() = TYPES[state].second
}

class GenreFilter(private val genres: List<GenreDto>) : Filter.Select<String>("Genre", arrayOf("Semua") + genres.map { it.name }) {
    val slug get() = genres.getOrNull(state - 1)?.slug
}
