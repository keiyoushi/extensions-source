package eu.kanade.tachiyomi.extension.es.lhentaiunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter : Filter.Select<String>("Categoría", CATEGORIES.map { it.first }.toTypedArray()) {
    val path get() = CATEGORIES[state].second
}

class TagFilter : Filter.Text("Etiqueta") {
    val slug get() = state.toSlug()
}

class ArtistFilter : Filter.Text("Artista") {
    val slug get() = state.toSlug()
}

private val CATEGORIES = arrayOf(
    "Todo" to "browse",
    "Manga" to "manga",
    "Doujinshi" to "doujinshi",
    "Occidental" to "western",
    "Set de imágenes" to "image-set",
    "CG de juego" to "game-cg",
    "CG de artista" to "artist-cg",
)

private val SLUG_SEPARATOR = Regex("[^a-z0-9]+")

private fun String.toSlug() = lowercase().replace(SLUG_SEPARATOR, "-").trim('-')

fun getFilters() = FilterList(
    Filter.Header("Los filtros se ignoran al buscar por texto; la etiqueta va antes que el artista"),
    CategoryFilter(),
    TagFilter(),
    ArtistFilter(),
)
