package eu.kanade.tachiyomi.multisrc.slimereadtheme

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

object SlimeReadThemeFilters {
    open class SelectFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        val selected get() = vals[state].second
    }

    private inline fun <reified R> FilterList.getSelected(): String {
        return (first { it is R } as SelectFilter).selected
    }

    open class CheckBoxFilterList(name: String, val pairs: Array<Pair<String, String>>) :
        Filter.Group<Filter.CheckBox>(name, pairs.map { CheckBoxVal(it.first) })

    private class CheckBoxVal(name: String) : Filter.CheckBox(name, false)

    private inline fun <reified R> FilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
    ): Sequence<String> {
        return (first { it is R } as CheckBoxFilterList).state
            .asSequence()
            .filter { it.state }
            .map { checkbox -> options.find { it.first == checkbox.name }!!.second }
    }

    internal class CategoriesFilter : CheckBoxFilterList("Categorias", SlimeReadFiltersData.CATEGORIES)

    internal class GenreFilter : SelectFilter("Gênero", SlimeReadFiltersData.GENRES)
    internal class SearchMethodFilter : SelectFilter("Método de busca", SlimeReadFiltersData.SEARCH_METHODS)
    internal class StatusFilter : SelectFilter("Status", SlimeReadFiltersData.STATUS)

    val FILTER_LIST get() = FilterList(
        CategoriesFilter(),
        GenreFilter(),
        SearchMethodFilter(),
        StatusFilter(),
    )

    data class FilterSearchParams(
        val categories: Sequence<String> = emptySequence(),
        val genre: String = "",
        val searchMethod: String = "",
        val status: String = "",
    )

    internal fun getSearchParameters(filters: FilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            filters.parseCheckbox<CategoriesFilter>(SlimeReadFiltersData.CATEGORIES),
            filters.getSelected<GenreFilter>(),
            filters.getSelected<SearchMethodFilter>(),
            filters.getSelected<StatusFilter>(),
        )
    }

    private object SlimeReadFiltersData {
        val CATEGORIES = arrayOf(
            Pair("Adulto", "125"),
            Pair("Artes Marciais", "117"),
            Pair("Avant Garde", "154"),
            Pair("Aventura", "112"),
            Pair("Ação", "146"),
            Pair("Comédia", "147"),
            Pair("Culinária", "126"),
            Pair("Doujinshi", "113"),
            Pair("Drama", "148"),
            Pair("Ecchi", "127"),
            Pair("Erotico", "152"),
            Pair("Esporte", "135"),
            Pair("Fantasia", "114"),
            Pair("Ficção Científica", "120"),
            Pair("Filosofico", "150"),
            Pair("Harém", "128"),
            Pair("Histórico", "115"),
            Pair("Isekai", "129"),
            Pair("Josei", "116"),
            Pair("Mecha", "130"),
            Pair("Militar", "149"),
            Pair("Mistério", "142"),
            Pair("Médico", "118"),
            Pair("One-shot", "131"),
            Pair("Premiado", "155"),
            Pair("Psicológico", "119"),
            Pair("Romance", "141"),
            Pair("Seinen", "140"),
            Pair("Shoujo", "133"),
            Pair("Shoujo-ai", "121"),
            Pair("Shounen", "139"),
            Pair("Shounen-ai", "134"),
            Pair("Slice-of-life", "122"),
            Pair("Sobrenatural", "123"),
            Pair("Sugestivo", "153"),
            Pair("Terror", "144"),
            Pair("Thriller", "151"),
            Pair("Tragédia", "137"),
            Pair("Vida Escolar", "132"),
            Pair("Yaoi", "124"),
            Pair("Yuri", "136"),
        )

        private val SELECT = Pair("Selecione", "")

        val GENRES = arrayOf(
            SELECT,
            Pair("Manga", "29"),
            Pair("Light Novel", "34"),
            Pair("Manhua", "31"),
            Pair("Manhwa", "30"),
            Pair("Novel", "33"),
            Pair("Webcomic", "35"),
            Pair("Webnovel", "36"),
            Pair("Webtoon", "32"),
            Pair("4-Koma", "37"),
        )

        val SEARCH_METHODS = arrayOf(
            SELECT,
            Pair("Preciso", "0"),
            Pair("Geral", "1"),
        )

        val STATUS = arrayOf(
            SELECT,
            Pair("Em andamento", "1"),
            Pair("Completo", "2"),
            Pair("Dropado", "3"),
            Pair("Cancelado", "4"),
            Pair("Hiato", "5"),
        )
    }
}
