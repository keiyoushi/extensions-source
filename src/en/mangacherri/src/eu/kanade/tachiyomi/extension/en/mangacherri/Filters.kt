package eu.kanade.tachiyomi.extension.en.mangacherri

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

class GenreFilter(genres: List<Pair<String, String>>) : QuerySelectFilter("Genre", "genre", listOf("Every genre" to "") + genres)

class StatusFilter :
    QuerySelectFilter(
        "Status",
        "status",
        listOf(
            "Any status" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        ),
    )

class SortFilter :
    QuerySelectFilter(
        "Order",
        "sort",
        listOf(
            "Recently updated" to "updated",
            "Newly added" to "added",
            "Most viewed" to "popular",
            "Most chapters" to "chapters",
            "Title A–Z" to "title",
            "Title Z–A" to "title_desc",
        ),
        2,
    )
