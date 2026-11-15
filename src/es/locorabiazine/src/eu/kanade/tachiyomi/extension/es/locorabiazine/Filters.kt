package eu.kanade.tachiyomi.extension.es.locorabiazine

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("La búsqueda se hace en los títulos de la galería elegida"),
    GalleryFilter(),
)

class GalleryFilter :
    Filter.Select<String>(
        "Galería",
        GALLERIES.map { it.first }.toTypedArray(),
    ) {
    val path get() = GALLERIES[state].second

    companion object {
        private val GALLERIES = listOf(
            "Series" to "work",
            "Completas" to "completas",
            "Infanto juveniles" to "viernes-a-capa-y-espada",
            "Manga" to "manga",
        )
    }
}
