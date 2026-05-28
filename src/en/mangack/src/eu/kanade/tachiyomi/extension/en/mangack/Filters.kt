package eu.kanade.tachiyomi.extension.en.mangack

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

internal class TaxonomyOption(val id: Int, val name: String)

internal interface UriFilter {
    fun applyTo(builder: HttpUrl.Builder)
}

internal open class UriSelectFilter(
    name: String,
    private val paramName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UriFilter {

    override fun applyTo(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) {
            builder.addQueryParameter(paramName, value)
        }
    }
}

internal class TypeFilter :
    UriSelectFilter(
        name = "Type",
        paramName = "comic-type",
        options = arrayOf(
            "Any" to "",
            "Manga" to "30",
            "Manhua" to "31",
            "Manhwa" to "32",
            "OEL" to "1305",
        ),
    )

internal class StatusFilter :
    UriSelectFilter(
        name = "Status",
        paramName = "manga-status",
        options = arrayOf(
            "Any" to "",
            "Ongoing" to "34",
            "Completed" to "35",
            "Hiatus" to "347",
            "Updating" to "839",
        ),
    )

internal class SortFilter :
    Filter.Sort(
        "Sort by",
        arrayOf("Date posted", "Date updated", "Title", "Slug"),
        Selection(0, ascending = false),
    ),
    UriFilter {

    override fun applyTo(builder: HttpUrl.Builder) {
        val orderBy = when (state?.index) {
            1 -> "modified"
            2 -> "title"
            3 -> "slug"
            else -> "date"
        }
        val direction = if (state?.ascending == true) "asc" else "desc"
        builder.addQueryParameter("orderby", orderBy)
        builder.addQueryParameter("order", direction)
    }
}

internal class YearFilter(years: List<TaxonomyOption>) :
    Filter.Select<String>(
        "Year",
        (listOf("Any") + years.map { it.name }).toTypedArray(),
    ),
    UriFilter {

    private val ids: List<Int> = listOf(-1) + years.map { it.id }

    override fun applyTo(builder: HttpUrl.Builder) {
        val id = ids[state]
        if (id > 0) builder.addQueryParameter("realised", id.toString())
    }
}

internal class GenreEntry(val term: TaxonomyOption) : Filter.TriState(term.name, STATE_IGNORE)

internal class GenreFilterGroup(genres: List<TaxonomyOption>) :
    Filter.Group<GenreEntry>("Genres", genres.map(::GenreEntry)),
    UriFilter {

    override fun applyTo(builder: HttpUrl.Builder) {
        val includes = state.filter { it.state == STATE_INCLUDE }
        val excludes = state.filter { it.state == STATE_EXCLUDE }
        if (includes.isNotEmpty()) {
            builder.addQueryParameter("Genres", includes.joinToString(",") { it.term.id.toString() })
        }
        if (excludes.isNotEmpty()) {
            builder.addQueryParameter("Genres_exclude", excludes.joinToString(",") { it.term.id.toString() })
        }
    }

    companion object {
        const val STATE_INCLUDE = Filter.TriState.STATE_INCLUDE
        const val STATE_EXCLUDE = Filter.TriState.STATE_EXCLUDE
    }
}

internal class WarningHeader(message: String) : Filter.Header(message)
