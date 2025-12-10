package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    CategoryFilter("Categoria", getCategories()),
)

class CategoryFilter(displayName: String, private val categories: List<Pair<String, String>>) :
    Filter.Select<String>(displayName, categories.map { it.first }.toTypedArray()) {
    val selected: String?
        get() = if (state == 0) null else categories.getOrNull(state)?.second
}

private fun getCategories() = listOf(
    Pair("Todas", ""),
    Pair("4Koma", "31"),
    Pair("Ação", "24"),
    Pair("Adulto", "84"),
    Pair("Artes Marciais", "21"),
    Pair("Aventura", "25"),
    Pair("Comédia", "26"),
    Pair("Culinária", "66"),
    Pair("Doujinshi", "78"),
    Pair("Drama", "22"),
    Pair("Ecchi", "12"),
    Pair("Escolar", "30"),
    Pair("Esporte", "76"),
    Pair("Fantasia", "23"),
    Pair("Harém", "29"),
    Pair("Histórico", "75"),
    Pair("Horror", "83"),
    Pair("Isekai", "18"),
    Pair("Josei", "122"),
    Pair("Light Novel", "20"),
    Pair("Manhua", "61"),
    Pair("Militar", "113"),
    Pair("Mistério", "124"),
    Pair("Oneshot", "108"),
    Pair("Psicológico", "56"),
    Pair("Romance", "7"),
    Pair("Sci-fi", "27"),
    Pair("seine", "125"),
    Pair("Seinen", "28"),
    Pair("Shoujo", "55"),
    Pair("Shounen", "54"),
    Pair("Slice of life", "19"),
    Pair("Sobrenatural", "17"),
    Pair("Tragédia", "57"),
    Pair("Vida Escolar", "109"),
    Pair("Webtoon", "62"),
    Pair("Yuri", "116"),
    Pair("Zumbi", "123"),
)
