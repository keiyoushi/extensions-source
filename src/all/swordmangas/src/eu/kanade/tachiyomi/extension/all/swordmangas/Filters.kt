package eu.kanade.tachiyomi.extension.all.swordmangas

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort",
        SORTS.map { it.first }.toTypedArray(),
    ) {
    val value get() = SORTS[state].second
}

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf("All") + GENRES,
    ) {
    val value get() = if (state == 0) null else GENRES[state - 1]
}

private val SORTS = listOf(
    "Most popular" to "popular",
    "Newest" to "newest",
    "Oldest" to "oldest",
    "A-Z" to "az",
)

// The site's own genre list, duplicates included
private val GENRES = arrayOf(
    "academia", "accion", "acción", "apasionado", "apocaliptico", "artes marciales", "aventura", "batalla",
    "comedia", "comida", "deporte", "deportes", "drama", "drama historico", "exorcismo", "fantasia", "Fantasia",
    "Fantasía", "guerra", "medicina", "murim", "musica", "ocultismo", "oficina", "reencarnacion", "rival",
    "romance", "school life", "sistema", "superheroe", "venganza", "vida escolar",
)
