@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Select<String>("Gêneros", genreOptions.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = genreOptions[state].second
}

class TypeFilter : Filter.Select<String>("Tipo", typeOptions.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = typeOptions[state].second
}

class StatusFilter : Filter.Select<String>("Status", statusOptions.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = statusOptions[state].second
}

class SortFilter : Filter.Select<String>("Ordenar por", sortOptions.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = sortOptions[state].second
}

private val genreOptions = listOf(
    "Gêneros" to "",
    "Ação" to "Ação",
    "Aventura" to "Aventura",
    "Artes Marciais" to "Artes Marciais",
    "Comédia" to "Comédia",
    "Drama" to "Drama",
    "Ecchi" to "Ecchi",
    "Fantasia" to "Fantasia",
    "Ficção Científica" to "Ficção Científica",
    "Harem" to "Harem",
    "Histórico" to "Histórico",
    "Maduro" to "Maduro",
    "Mistério" to "Mistério",
    "Psicológico" to "Psicológico",
    "Romance" to "Romance",
    "Seinen" to "Seinen",
    "Shoujo" to "Shoujo",
    "Shounen" to "Shounen",
    "Sobrenatural" to "Sobrenatural",
    "Tragédia" to "Tragédia",
    "Vida Escolar" to "Vida Escolar",
)

private val typeOptions = listOf(
    "Todos" to "all",
    "Mangá" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Novel" to "novel",
    "Yaoi" to "yaoi",
    "Yuri" to "yuri",
    "Shoujo" to "shoujo",
    "English" to "english",
    "Webtoon" to "webtoon",
    "Doujinshi" to "doujinshi",
    "Hentai" to "hentai",
    "Pornhwa" to "pornhwa",
)

private val statusOptions = listOf(
    "Status" to "all",
    "Em lançamento" to "ONGOING",
    "Completo" to "COMPLETED",
    "Hiato" to "HIATUS",
    "Cancelado" to "CANCELED",
)

private val sortOptions = listOf(
    "Mais Populares" to "popular",
    "Maior Avaliação" to "rating",
    "Mais Recentes" to "recent",
    "Novidades" to "new",
    "A-Z" to "alphabetical",
)
