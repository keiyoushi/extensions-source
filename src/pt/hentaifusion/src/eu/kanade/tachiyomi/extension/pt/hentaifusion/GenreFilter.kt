package eu.kanade.tachiyomi.extension.pt.hentaifusion

import eu.kanade.tachiyomi.source.model.Filter

private val GENRES = listOf(
    "" to "Todos",
    "doujin" to "Doujin",
    "hentai" to "Hentai",
    "hq-hentai" to "HQ Hentai",
    "mangas-hentai" to "Mangás Hentai",
    "quadrinhos-eroticos" to "Quadrinhos Eróticos",
)

class GenreFilter :
    Filter.Select<String>(
        "Gênero",
        GENRES.map { it.second }.toTypedArray(),
    ) {
    fun selectedSlug(): String? = GENRES[state].first.ifEmpty { null }
}
