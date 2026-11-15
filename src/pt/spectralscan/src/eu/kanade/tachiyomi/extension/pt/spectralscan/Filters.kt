package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.source.model.Filter

class SelectFilter(
    displayName: String,
    val parameter: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun selected() = vals[state].second
}

class CheckboxGroup(
    displayName: String,
    val parameter: String,
    val items: List<CheckboxItem>,
) : Filter.Group<CheckboxItem>(displayName, items) {
    fun selected() = items.filter { it.state }.map { it.value }
}

class CheckboxItem(
    displayName: String,
    val value: String,
) : Filter.CheckBox(displayName, false)

val sortList = arrayOf(
    "Visualizações" to "views",
    "Atualização" to "updatedAt",
    "Adicionado" to "created",
    "Título" to "title",
    "Avaliações" to "rating",
    "Capitulos" to "chapters",
    "Ano" to "releaseYear",
)

val orderList = arrayOf(
    "Decrescente" to "desc",
    "Crescente" to "asc",
)

val statusList = arrayOf(
    Pair("Em Andamento", "ongoing"),
    Pair("Completo", "completed"),
    Pair("Cancelado", "cancelled"),
    Pair("Hiato", "hiatus"),
)

val typeList = arrayOf(
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Manhua", "manhua"),
    Pair("Webtoon", "webtoon"),
    Pair("Comic", "comic"),
    Pair("HQ", "hq"),
    Pair("Pornhwa", "pornhwa"),
)

val categoryModeList = arrayOf(
    "Qualquer categoria (OU)" to "or",
    "Todas categorias (E)" to "and",
)
