package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.Filter

private val GENRES = arrayOf(
    null to "Todos",
    "romance" to "Romance",
    "drama" to "Drama",
    "fantasia" to "Fantasía",
    "comedia" to "Comedia",
    "accion" to "Acción",
    "aventura" to "Aventura",
    "harem" to "Harem",
    "isekai" to "Isekai",
    "manhwa" to "Manhwa",
    "manga" to "Manga",
    "manhua" to "Manhua",
    "shounen" to "Shounen",
    "seinen" to "Seinen",
    "bl" to "BL",
    "yaoi" to "Yaoi",
    "yuri" to "Yuri",
    "18" to "+18",
    "sin-censura" to "Sin censura",
)

class GenreFilter : Filter.Select<String>("Género", GENRES.map { it.second }.toTypedArray()) {
    // null for "Todos" so the caller omits the query parameter
    fun toUriPart(): String? = GENRES[state].first
}
