package eu.kanade.tachiyomi.extension.pt.noindexscan

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

internal interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

internal class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(parameter, value)
    }
}

internal class YearFilter(name: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        if (state.isNotBlank()) url.addQueryParameter("ano", state.trim())
    }
}

internal class GenreOption(name: String, val slug: String) : Filter.TriState(name)

internal class GenreFilter(name: String, genres: List<GenreRoute>) :
    Filter.Group<GenreOption>(name, genres.map { GenreOption(it.name, it.slug) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.forEach {
            when {
                it.isIncluded() -> url.addQueryParameter("genero[]", it.slug)
                it.isExcluded() -> url.addQueryParameter("sem-genero[]", it.slug)
            }
        }
    }
}

internal val SORT_OPTIONS = listOf(
    "Atualizações" to "",
    "Adicionadas recentemente" to "newest",
    "Nome: A–Z" to "alphabet",
    "Nota" to "rating",
)

internal val TYPE_OPTIONS = listOf(
    "Todos" to "",
    "Manhwa" to "Manhwa",
    "Pornhwa" to "Pornhwa",
    "Doujin" to "Doujin",
    "Doujinshi" to "Doujinshi",
)

internal val STATUS_OPTIONS = listOf(
    "Todos" to "",
    "Em andamento" to "on-going",
    "Completa" to "end",
    "Cancelada" to "canceled",
    "Em espera" to "on-hold",
    "Em breve" to "upcoming",
)

internal val CHAPTER_COUNT_OPTIONS = listOf(
    "Qualquer quantidade" to "",
    "De 1 a 50" to "short",
    "De 51 a 100" to "medium",
    "Mais de 100" to "long",
)
