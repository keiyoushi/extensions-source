package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    GeneroFilter(),
    FormatoFilter(),
    StatusFilter(),
    TagFilter(),
    SortFilter(),
)

// ========================= Base Classes =========================

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val queryParam: String,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String?
        get() = if (state > 0) options[state].second else null

    val param: String
        get() = queryParam
}

open class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(name, options.map { CheckBoxFilter(it.first, it.second) }) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }
}

// ========================= Filter Implementations =========================

class GeneroFilter : SelectFilter(
    "Gênero",
    listOf(
        Pair("Todos", ""),
        Pair("Livres", "1"),
        Pair("Shoujo / Romances", "4"),
        Pair("Hentais", "5"),
        Pair("Novel", "6"),
        Pair("Yaoi", "7"),
        Pair("Mangás", "8"),
    ),
    "gen_id",
)

class FormatoFilter : SelectFilter(
    "Formato",
    listOf(
        Pair("Todos", ""),
        Pair("Manhwa", "1"),
        Pair("Manhua", "2"),
        Pair("Mangá", "3"),
        Pair("Novel", "4"),
    ),
    "formt_id",
)

class StatusFilter : SelectFilter(
    "Status",
    listOf(
        Pair("Todos", ""),
        Pair("Em Andamento", "1"),
        Pair("Concluído", "2"),
        Pair("Hiato", "3"),
        Pair("Cancelado", "4"),
    ),
    "stt_id",
)

class SortFilter : SelectFilter(
    "Ordenar por",
    listOf(
        Pair("Última Atualização", "ultima_atualizacao"),
        Pair("Mais Visualizados", "visualizacoes_geral"),
        Pair("Lançamentos", "criacao"),
        Pair("Melhor Avaliação", "rating"),
        Pair("Nome A-Z", "obr_nome"),
    ),
    "orderBy",
)

class TagFilter : CheckBoxGroup(
    "Tags",
    listOf(
        Pair("Ação", "1"),
        Pair("Aventura", "2"),
        Pair("Comédia", "3"),
        Pair("Drama", "4"),
        Pair("Fantasia", "5"),
        Pair("Terror", "6"),
        Pair("Mistério", "7"),
        Pair("Romance", "8"),
        Pair("Sci-Fi", "9"),
        Pair("Slice of Life", "10"),
        Pair("Esportes", "11"),
        Pair("Thriller", "12"),
        Pair("Sobrenatural", "13"),
        Pair("Histórico", "14"),
        Pair("Mecha", "15"),
        Pair("Psicológico", "16"),
        Pair("Seinen", "17"),
        Pair("Shoujo", "18"),
        Pair("Shounen", "19"),
        Pair("Josei", "20"),
        Pair("Isekai", "21"),
        Pair("Artes Marciais", "22"),
        Pair("Gore", "23"),
        Pair("Yuri", "24"),
        Pair("Yaoi", "25"),
        Pair("Escolar", "26"),
        Pair("Apocalipse", "28"),
        Pair("Ecchi", "36"),
        Pair("Hárem", "44"),
        Pair("Magia", "50"),
        Pair("Murim", "52"),
        Pair("Reencarnação", "58"),
        Pair("Regressão", "59"),
        Pair("Sistema", "61"),
        Pair("Vingança", "67"),
    ),
)
