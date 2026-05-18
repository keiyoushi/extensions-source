@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.mangadash

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Ordenação",
        arrayOf(
            Pair("Mais Recentes", "recentes"),
            Pair("Mais Vistos", "populares"),
            Pair("A-Z", "alfabetica"),
            Pair("Melhor Avaliados", "nota"),
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "Categoria",
        arrayOf(
            Pair("Todas", ""),
            Pair("Ação", "acao"),
            Pair("Aventura", "aventura"),
            Pair("Comédia", "comedia"),
            Pair("Cotidianos", "cotidianos"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Esportes", "esportes"),
            Pair("Fantasia", "fantasia"),
            Pair("Ficção Científica", "ficcao-cientifica"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Histórico", "historico"),
            Pair("Isekai", "isekai"),
            Pair("Mistério", "misterio"),
            Pair("Música", "musica"),
            Pair("Psicológico", "psicologico"),
            Pair("Romance", "romance"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Suspense", "suspense"),
            Pair("Terror", "terror"),
            Pair("Tragedy", "tragedy"),
            Pair("YAOI", "yaoi"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Todos", ""),
            Pair("Em Lançamento", "Em Lançamento"),
            Pair("Concluído", "Concluído"),
            Pair("Em Hiato", "Em hiato"),
        ),
    )

class YearFilter :
    UriPartFilter(
        "Ano",
        arrayOf(
            Pair("Todos", ""),
            Pair("2026", "2026"),
            Pair("2025", "2025"),
            Pair("2024", "2024"),
            Pair("2023", "2023"),
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("2017", "2017"),
            Pair("2016", "2016"),
            Pair("2015", "2015"),
            Pair("2014", "2014"),
            Pair("2012", "2012"),
            Pair("2011", "2011"),
            Pair("2009", "2009"),
            Pair("2007", "2007"),
            Pair("2006", "2006"),
            Pair("2005", "2005"),
            Pair("2003", "2003"),
            Pair("2002", "2002"),
            Pair("2001", "2001"),
            Pair("1999", "1999"),
            Pair("1998", "1998"),
            Pair("1997", "1997"),
            Pair("1993", "1993"),
            Pair("1991", "1991"),
            Pair("1989", "1989"),
            Pair("1985", "1985"),
        ),
    )

class Plus18Filter : Filter.CheckBox("+18 (Apenas Adulto)", false)
