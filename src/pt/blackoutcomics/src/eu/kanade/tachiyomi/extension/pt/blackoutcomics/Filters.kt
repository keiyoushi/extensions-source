package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val selected: String? get() = STATUS_OPTIONS[state].second
}

class GenreFilter :
    Filter.Select<String>(
        "Gênero",
        GENRE_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val selected: String? get() = GENRE_OPTIONS[state].second
}

private val STATUS_OPTIONS = arrayOf(
    "Todos" to null,
    "Completos" to "completed",
)

private val GENRE_OPTIONS = arrayOf(
    "Todos" to null,
    "Adulto" to "Adulto",
    "Drama" to "Drama",
    "Harém" to "Harém",
    "Romance" to "Romance",
    "Comédia" to "Comédia",
    "Ação" to "Ação",
    "Fantasia" to "Fantasia",
    "Mistério" to "Mistério",
    "Isekai" to "Isekai",
    "Vida Universitária" to "Vida Universitária",
    "Manhwa" to "Manhwa",
    "Pornhwa" to "Pornhwa",
    "Webtoon" to "Webtoon",
)
