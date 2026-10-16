package eu.kanade.tachiyomi.extension.en.keroscans

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface QueryParamFilter {
    fun addQueryParameter(urlBuilder: HttpUrl.Builder)
}

internal open class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

internal open class TriStateGroupFilter(name: String, val id: String, state: Map<String, String>) :
    Filter.Group<TriStateFilter>(name, state.map { TriStateFilter(it.key, it.value) }),
    QueryParamFilter {
    override fun addQueryParameter(urlBuilder: HttpUrl.Builder) {
        state.filter { it.state == TriState.STATE_INCLUDE }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }?.let {
            urlBuilder.addQueryParameter(id, it)
        }
        state.filter { it.state == TriState.STATE_EXCLUDE }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }?.let {
            urlBuilder.addQueryParameter("ex$id", it)
        }
    }
}

internal open class CheckBoxFilter(name: String, val id: String) :
    Filter.CheckBox(name),
    QueryParamFilter {
    override fun addQueryParameter(urlBuilder: HttpUrl.Builder) {
        urlBuilder.addQueryParameter(id, "$state")
    }
}

internal open class MultiSelectFilter(name: String, val id: String, state: Map<String, String>, val defaultSelectAll: Boolean = false) :
    Filter.Group<CheckBoxFilter>(name, state.map { CheckBoxFilter(it.key, it.value) }),
    QueryParamFilter {
    override fun addQueryParameter(urlBuilder: HttpUrl.Builder) {
        val selected = state
            .filter { it.state }
            .takeIf { it.isNotEmpty() }
            ?: state.takeIf { defaultSelectAll }

        selected?.joinToString(",") { it.id }.let {
            urlBuilder.addQueryParameter(id, it)
        }
    }
}

internal open class RangeFilter(name: String, val id: String) :
    Filter.Text("$name Range (low-high, low+, high-)"),
    QueryParamFilter {
    override fun addQueryParameter(urlBuilder: HttpUrl.Builder) {
        val min = minRegex.find(state)?.groupValues[0]
        val max = maxRegex.find(state)?.groupValues[0]
        if (state.isNotEmpty() && min == null && max == null) {
            throw Exception("Invalid range")
        }
        min?.let { urlBuilder.addQueryParameter("${id}_min", it) }
        max?.let { urlBuilder.addQueryParameter("${id}_max", it) }
    }

    companion object {
        val minRegex = """^(\d+)(?:\+|-\d+|)$""".toRegex()
        val maxRegex = """^(?:\d+-|)(\d+)-?$""".toRegex()
    }
}

internal open class SelectFilter(name: String, val id: String, val mapping: Map<String, String>) :
    Filter.Select<String>(name, mapping.keys.toTypedArray()),
    QueryParamFilter {
    override fun addQueryParameter(urlBuilder: HttpUrl.Builder) {
        mapping[values[state]]?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter(id, it) }
    }
}

private val types = mapOf(
    "Manhwa" to "Manhwa",
    "Manhua" to "Manhua",
    "Manga" to "Manga",
    "Webtoon" to "Webtoon",
)

private val statuses = mapOf(
    "Ongoing" to "Ongoing",
    "Completed" to "Completed",
    "Hiatus" to "Hiatus",
    "Dropped" to "Dropped",
    "Discontinued" to "Discontinued",
    "Upcoming" to "Upcoming",
)

private val origins = mapOf(
    "Korean" to "KOREAN",
    "Japanese" to "JAPANESE",
    "Chinese" to "CHINESE",
    "Other" to "OTHER",
)

private val sorts = mapOf(
    "Recently Updated" to "",
    "Most Bookmarked" to "popular",
    "Most Viewed" to "views",
    "Longest" to "longest",
    "Trending" to "trending",
    "Top Rated" to "rating",
    "Newest" to "newest",
)

internal class GenreFilter(genres: Map<String, String>) : TriStateGroupFilter("Genres", "genre", genres)

internal class TagFilter(tags: Map<String, String>) : TriStateGroupFilter("Tags", "tag", tags)

// By default, all comic types are included in the query param to make sure novels are excluded
internal class TypeFilter : MultiSelectFilter("Type", "type", types, defaultSelectAll = true)

internal class StatusFilter : MultiSelectFilter("Status", "status", statuses)

internal class OriginFilter : MultiSelectFilter("Origins", "origin", origins)

internal class SaleFilter : CheckBoxFilter("On Sale", "sale")

internal class ChapterFilter : RangeFilter("Chapter", "ch")

internal class SortFilter : SelectFilter("Sort", "sort", sorts)
