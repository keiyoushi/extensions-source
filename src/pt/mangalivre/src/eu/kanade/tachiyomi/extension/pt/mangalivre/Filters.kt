package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.time.Year

class SortFilter(state: Selection = Selection(0, false)) :
    Filter.Sort(
        "Ordenar por",
        OPTIONS.map { it.first }.toTypedArray(),
        state,
    ) {
    val sortBy get() = OPTIONS[state!!.index].second
    val ascending get() = state!!.ascending

    companion object {
        private val OPTIONS = listOf(
            "Mais Visualizadas" to "popular",
            "Lançamentos" to "release",
            "Última Atualização" to "updated",
            "Melhor Avaliação" to "rating",
            "A-Z" to "title",
        )
    }
}

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, private val parameter: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }) {
    fun addTo(url: HttpUrl.Builder) = state.filter { it.state }.forEach { url.addQueryParameter(parameter, it.value) }
}

class TypeFilter : CheckBoxGroup("Tipo", "type", listOf("Manga", "Manhwa", "Manhua", "Webtoon").map { it to it })

class StatusFilter :
    CheckBoxGroup(
        "Status",
        "status",
        listOf(
            "Em Lançamento" to "Ongoing",
            "Completo" to "Completed",
            "Em Hiato" to "Hiatus",
            "Cancelado" to "Canceled",
        ),
    )

class YearFilter : CheckBoxGroup("Ano", "year", (0..10).map { (Year.now().value - it).toString() }.map { it to it })

class GenreFilter(genres: List<String>) : CheckBoxGroup("Gêneros", "genres", genres.map { it to it })
