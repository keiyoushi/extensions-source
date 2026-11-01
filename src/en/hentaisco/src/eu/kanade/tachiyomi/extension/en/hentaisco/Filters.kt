package eu.kanade.tachiyomi.extension.en.hentaisco

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface QueryFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

open class QuerySelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), default),
    QueryFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) builder.addQueryParameter(param, value)
    }
}

class SortFilter :
    QuerySelectFilter(
        "Sort by",
        "m_orderby",
        listOf(
            "Latest" to "",
            "A–Z" to "alphabet",
            "Rating" to "rating",
            "Trending" to "trending",
            "Most viewed" to "views",
            "New" to "new-manga",
        ),
        4,
    )

class StatusFilter :
    QuerySelectFilter(
        "Status",
        "status",
        listOf(
            "Any status" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "On hiatus" to "hiatus",
            "Dropped" to "dropped",
        ),
    )

class TypeFilter :
    QuerySelectFilter(
        "Type",
        "type",
        listOf(
            "Any type" to "",
            "Manhwa" to "manhwa",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Doujinshi" to "doujinshi",
            "Webtoon" to "webtoon",
            "CG" to "cg",
            "Other" to "other",
        ),
    )

class GenreFilter(private val genres: Map<String, String>) : Filter.Select<String>("Genre", arrayOf("Any genre") + genres.keys) {
    val slug get() = genres.values.elementAtOrNull(state - 1)
}
