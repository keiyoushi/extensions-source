package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(
    name: String,
    val queryName: String,
    private val options: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    fun selectedValue(): String = options.getOrNull(state)?.second.orEmpty()
}

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Todos os status", ""),
            Pair("Em andamento", "ongoing"),
            Pair("Finalizada", "finished"),
            Pair("Hiato", "hiatus"),
            Pair("Dropada", "dropped"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        arrayOf(
            Pair("Todos os tipos", ""),
            Pair("Manhwa", "manhwa"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "sort",
        arrayOf(
            Pair("Adicionado", "added"),
            Pair("Atualizado", "updated"),
            Pair("Avaliação", "rating"),
            Pair("Título", "title"),
        ),
        state = 1,
    )

class SortOrderFilter :
    SelectFilter(
        "Ordem",
        "order",
        arrayOf(
            Pair("Decrescente", "desc"),
            Pair("Crescente", "asc"),
        ),
    )

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Gêneros",
        listOf(
            Pair("Ação", "acao"),
            Pair("Adulto", "adulto"),
            Pair("Apocalíptico", "apocaliptico"),
            Pair("Artes Marciais", "artes-marciais"),
            Pair("Aventura", "aventura"),
            Pair("Comédia", "comedia"),
            Pair("Crime / Policial", "crime-policial"),
            Pair("Culinária", "culinaria"),
            Pair("Cultivação", "cultivacao"),
            Pair("Cultivo", "cultivo"),
            Pair("Drama", "drama"),
            Pair("Escolar", "escolar"),
            Pair("Esportes", "esportes"),
            Pair("Estratégia", "estrategia"),
            Pair("Fantasia", "fantasia"),
            Pair("Gore", "gore"),
            Pair("Guerra", "guerra"),
            Pair("Harém", "harem"),
            Pair("Histórico", "historico"),
            Pair("Idol", "idol"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Máfia", "mafia"),
            Pair("Magia", "magia"),
            Pair("Mistério", "misterio"),
            Pair("Murim", "murim"),
            Pair("Música", "musica"),
            Pair("Psicologia", "psicologia"),
            Pair("Reencarnação", "reencarnacao"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Sobrevivência", "sobrevivencia"),
            Pair("Superpoderes", "superpoderes"),
            Pair("Suspense", "suspense"),
            Pair("Terror", "terror"),
            Pair("Thriller", "thriller"),
            Pair("Tragédia", "tragedia"),
            Pair("Vingança", "vinganca"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ).map { (name, slug) -> GenreCheckBox(name, slug) },
    ) {
    fun includedGenreSlugs(): List<String> = state
        .filter { it.state }
        .map { it.slug }
}

fun getFilters(): FilterList = FilterList(
    StatusFilter(),
    TypeFilter(),
    SortFilter(),
    SortOrderFilter(),
    Filter.Separator(),
    GenreFilter(),
)
