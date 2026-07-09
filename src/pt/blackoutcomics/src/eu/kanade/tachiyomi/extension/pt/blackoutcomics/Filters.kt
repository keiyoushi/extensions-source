package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart() = STATUS_OPTIONS[state].second
}

class GenreFilter :
    Filter.Select<String>(
        "Gênero",
        GENRE_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart() = GENRE_OPTIONS[state].second
}

private val STATUS_OPTIONS = arrayOf(
    Pair("Todos", ""),
    Pair("Completos", "completed"),
)

private val GENRE_OPTIONS = arrayOf(
    Pair("Todos", ""),
    Pair("Adulto", "Adulto"),
    Pair("Drama", "Drama"),
    Pair("Harém", "Harém"),
    Pair("Romance", "Romance"),
    Pair("Comédia", "Comédia"),
    Pair("Ação", "Ação"),
    Pair("Fantasia", "Fantasia"),
    Pair("Mistério", "Mistério"),
    Pair("Isekai", "Isekai"),
    Pair("Vida Universitária", "Vida Universitária"),
    Pair("Manhwa", "Manhwa"),
    Pair("Pornhwa", "Pornhwa"),
    Pair("Webtoon", "Webtoon"),
)
