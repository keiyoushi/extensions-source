package eu.kanade.tachiyomi.extension.es.anzmanga

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter : Filter.Select<String>("Categoría", categories.map { it.first }.toTypedArray()) {
    fun toUriPart() = categories[state].second
}

class SortFilter : Filter.Sort("Ordenar por", sortables.map { it.first }.toTypedArray(), Selection(1, false)) {
    fun toUriPart() = sortables[state?.index ?: 1].second
    fun isAscending() = state?.ascending ?: false
}

private val categories = arrayOf(
    Pair("Todas", ""),
    Pair("Acción", "1"),
    Pair("Aventura", "2"),
    Pair("Comedia", "3"),
    Pair("Doujinshi", "4"),
    Pair("Drama", "5"),
    Pair("Ecchi", "6"),
    Pair("Fantasía", "7"),
    Pair("Gender Bender", "8"),
    Pair("Harem", "9"),
    Pair("Histórico", "10"),
    Pair("Horror", "11"),
    Pair("Josei", "12"),
    Pair("Artes Marciales", "13"),
    Pair("Mature", "14"),
    Pair("Mecha", "15"),
    Pair("Misterio", "16"),
    Pair("One Shot", "17"),
    Pair("Psicológico", "18"),
    Pair("Romance", "19"),
    Pair("Escolares", "20"),
    Pair("Ciencia Ficción", "21"),
    Pair("Seinen", "22"),
    Pair("Shoujo", "23"),
    Pair("Shoujo Ai", "24"),
    Pair("Shounen", "25"),
    Pair("Shounen Ai", "26"),
    Pair("Recuentos de la vida", "27"),
    Pair("Deportes", "28"),
    Pair("Sobrenatural", "29"),
    Pair("Tragedia", "30"),
    Pair("Yaoi", "31"),
    Pair("Yuri", "32"),
    Pair("Magia", "33"),
    Pair("Gore", "34"),
    Pair("Manhwa", "35"),
    Pair("Manhua", "36"),
    Pair("Música", "37"),
)

private val sortables = arrayOf(
    Pair("AZ", "name"),
    Pair("Visitas", "views"),
)
