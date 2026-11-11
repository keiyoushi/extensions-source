package eu.kanade.tachiyomi.extension.pt.dropescan

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Ordenar por", SORTS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        SORTS[state].second?.let { url.addQueryParameter("sortBy", it) }
    }
}

private val SORTS = listOf(
    "Padrão" to null,
    "Melhor Match" to "relevance",
    "Título (A-Z)" to "titleAsc",
    "Título (Z-A)" to "titleDesc",
    "Adicionado Recentemente" to "recentlyAdded",
    "Adicionado Mais Antigo" to "oldestAdded",
    "Ano de Lançamento (Crescente)" to "yearAsc",
    "Ano de Lançamento (Decrescente)" to "yearDesc",
)

class Option(name: String, val value: String) : Filter.CheckBox(name)

open class OptionGroupFilter(name: String, options: List<Pair<String, String>>, private val parameter: String) :
    Filter.Group<Option>(name, options.map { Option(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter("$parameter[]", it.value) }
    }
}

class StatusFilter :
    OptionGroupFilter(
        "Status de Publicação",
        listOf(
            "Em Andamento" to "Active",
            "Inativo" to "Inactive",
            "Completo" to "Completed",
            "Hiato" to "Hiatus",
            "Cancelado" to "Cancelled",
        ),
        "status",
    )

class DemographicFilter :
    OptionGroupFilter(
        "Demografia",
        listOf(
            "Shounen" to "Shounen",
            "Shoujo" to "Shoujo",
            "Josei" to "Josei",
            "Seinen" to "Seinen",
            "Nenhum" to "Not Applicable",
        ),
        "publicationDemographic",
    )

class ContentRatingFilter :
    OptionGroupFilter(
        "Classificação de Conteúdo",
        listOf(
            "Seguro" to "Safe",
            "Sugestivo" to "Sugestive",
            "Erótico" to "Erotic",
            "Pornográfico" to "Pornografic",
        ),
        "contentRating",
    )

class ContentWarningFilter :
    OptionGroupFilter(
        "Avisos de Conteúdo",
        listOf(
            "Gore" to "Gore",
            "Violência Sexual" to "Sexual Violence",
        ),
        "contentWarning",
    )

class LanguageFilter :
    OptionGroupFilter(
        "Língua Original",
        listOf(
            "Japonês" to "jp",
            "Coreano" to "kr",
            "Chinês" to "cn",
            "Inglês" to "gb",
            "Português (Brasil)" to "br",
            "Português" to "pt",
            "Espanhol" to "es",
            "Francês" to "fr",
            "Alemão" to "de",
            "Italiano" to "it",
            "Russo" to "ru",
            "Indonésio" to "id",
            "Tailandês" to "th",
            "Vietnamita" to "vi",
            "Árabe" to "ar",
        ),
        "originalLanguage",
    )

class YearFilter :
    Filter.Text("Ano de Publicação"),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.trim().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("year", it) }
    }
}

class Tag(name: String, val id: String) : Filter.TriState(name)

class TagGroupFilter(name: String, tags: List<Pair<String, String>>) : Filter.Group<Tag>(name, tags.map { Tag(it.first, it.second) })

open class TagModeFilter(name: String) : Filter.Select<String>(name, arrayOf("OU", "E")) {
    val value get() = if (state == 0) "OR" else "AND"
}

class IncludedTagsModeFilter : TagModeFilter("Modo das tags incluídas")

class ExcludedTagsModeFilter : TagModeFilter("Modo das tags excluídas")
