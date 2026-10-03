package eu.kanade.tachiyomi.multisrc.liliana

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val urlParameter: String,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

abstract class TriStateGroupFilter(
    name: String,
    options: List<Pair<String, String>>,
    private val includeUrlParameter: String,
    private val excludeUrlParameter: String,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(
            includeUrlParameter,
            state.filter { it.isIncluded() }.joinToString(",") { it.id },
        )
        url.addQueryParameter(
            excludeUrlParameter,
            state.filter { it.isExcluded() }.joinToString(",") { it.id },
        )
    }
}

class GenreFilter(
    name: String,
    options: List<Pair<String, String>>,
) : TriStateGroupFilter(
    name,
    options,
    "genres",
    "notGenres",
)

class ChapterCountFilter(
    name: String,
    options: List<Pair<String, String>>,
) : SelectFilter(
    name,
    options,
    "chapter_count",
)

class StatusFilter(
    name: String,
    options: List<Pair<String, String>>,
) : SelectFilter(
    name,
    options,
    "status",
)

class GenderFilter(
    name: String,
    options: List<Pair<String, String>>,
) : SelectFilter(
    name,
    options,
    "sex",
)

class SortFilter(
    name: String,
    options: List<Pair<String, String>>,
) : SelectFilter(
    name,
    options,
    "sort",
)

@Serializable
class FilterData(
    val genreName: String,
    val genres: List<Pair<String, String>>,
    val chapterCountName: String,
    val chapterCounts: List<Pair<String, String>>,
    val statusName: String,
    val statuses: List<Pair<String, String>>,
    val genderName: String,
    val genders: List<Pair<String, String>>,
    val sortName: String,
    val sorts: List<Pair<String, String>>,
)
