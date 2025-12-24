package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Select<String>("Gênero", genres.map { it.first }.toTypedArray()) {
    fun toUriPart() = genres[state].second
}

class TypeFilter : Filter.Select<String>("Tipo", types.map { it.first }.toTypedArray()) {
    fun toUriPart() = types[state].second
}

class StatusFilter : Filter.Select<String>("Status", statusList.map { it.first }.toTypedArray()) {
    fun toUriPart() = statusList[state].second
}

class AdultFilter : Filter.CheckBox("Conteúdo +18", false)

class SortFilter : Filter.Select<String>("Ordenar por", sortList.map { it.first }.toTypedArray()) {
    fun toUriPart() = sortList[state].second
}

private val genres = listOf(
    Pair("Todos", ""),
    Pair("Ação", "Ação"),
    Pair("Aventura", "Aventura"),
    Pair("Artes Marciais", "Artes Marciais"),
    Pair("Comédia", "Comédia"),
    Pair("Drama", "Drama"),
    Pair("Ecchi", "Ecchi"),
    Pair("Fantasia", "Fantasia"),
    Pair("Ficção Científica", "Ficção Científica"),
    Pair("Harem", "Harem"),
    Pair("Histórico", "Histórico"),
    Pair("Maduro", "Maduro"),
    Pair("Mistério", "Mistério"),
    Pair("Psicológico", "Psicológico"),
    Pair("Romance", "Romance"),
    Pair("Seinen", "Seinen"),
    Pair("Shoujo", "Shoujo"),
    Pair("Shounen", "Shounen"),
    Pair("Sobrenatural", "Sobrenatural"),
    Pair("Tragédia", "Tragédia"),
    Pair("Vida Escolar", "Vida Escolar"),
)

private val types = listOf(
    Pair("Todos", ""),
    Pair("Manhwa", "MANHWA"),
    Pair("Mangá", "MANGA"),
    Pair("Manhua", "MANHUA"),
    Pair("Webtoon", "WEBTOON"),
)

private val statusList = listOf(
    Pair("Todos", ""),
    Pair("Em andamento", "ATIVO"),
    Pair("Completo", "CONCLUIDO"),
    Pair("Em hiato", "HIATO"),
)

private val sortList = listOf(
    Pair("Recentes", "createdAt"),
    Pair("Nome (A-Z)", "name"),
    Pair("Atualizados", "updatedAt"),
)
