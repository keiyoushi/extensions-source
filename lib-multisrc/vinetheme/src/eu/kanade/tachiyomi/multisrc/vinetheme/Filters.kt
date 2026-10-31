package eu.kanade.tachiyomi.multisrc.vinetheme

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

open class TriStateGroupFilter(name: String, val id: String, state: Map<String, String>) :
    Filter.Group<TriStateFilter>(name, state.map { TriStateFilter(it.key, it.value) }),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.filter { it.state == TriState.STATE_INCLUDE }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }?.let {
            builder.addQueryParameter(id, it)
        }
        state.filter { it.state == TriState.STATE_EXCLUDE }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }?.let {
            builder.addQueryParameter("ex$id", it)
        }
    }
}

open class CheckBoxFilter(name: String, val id: String) :
    Filter.CheckBox(name),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        builder.addQueryParameter(id, "$state")
    }
}

open class MultiSelectFilter(name: String, val id: String, state: Map<String, String>) :
    Filter.Group<CheckBoxFilter>(name, state.map { CheckBoxFilter(it.key, it.value) }),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.filter { it.state }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }?.let {
            builder.addQueryParameter(id, it)
        }
    }
}

open class RangeFilter(name: String, val id: String) :
    Filter.Text("$name Range (low-high, low+, high-)"),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val min = minRegex.find(state)?.groupValues[0]
        val max = maxRegex.find(state)?.groupValues[0]
        if (state.isNotEmpty() && min == null && max == null) {
            throw Exception("Invalid range")
        }
        min?.let { builder.addQueryParameter("${id}_min", it) }
        max?.let { builder.addQueryParameter("${id}_max", it) }
    }

    companion object {
        val minRegex = """^(\d+)(?:\+|-\d+|)$""".toRegex()
        val maxRegex = """^(?:\d+-|)(\d+)-?$""".toRegex()
    }
}

open class SelectFilter(name: String, val id: String, val mapping: Map<String, String>) :
    Filter.Select<String>(name, mapping.keys.toTypedArray()),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        mapping[values[state]]?.takeIf { it.isNotEmpty() }?.let { builder.addQueryParameter(id, it) }
    }
}

internal class GenreFilter(genres: Map<String, String>) : TriStateGroupFilter("Genres", "genre", genres)

internal class TagFilter(tags: Map<String, String>) : TriStateGroupFilter("Tags", "tag", tags)

internal class TypeFilter : MultiSelectFilter("Type", "type", state) {
    companion object {
        val state = mapOf(
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
            "Manga" to "Manga",
            "Webtoon" to "Webtoon",
        )
    }
}

internal class StatusFilter : MultiSelectFilter("Status", "status", state) {
    companion object {
        val state = mapOf(
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
            "Dropped" to "Dropped",
            "Discontinued" to "Discontinued",
            "Upcoming" to "Upcoming",
        )
    }
}

internal class OriginFilter : MultiSelectFilter("Origin", "origin", state) {
    companion object {
        val state = mapOf(
            "Korean" to "KOREAN",
            "Japanese" to "JAPANESE",
            "Chinese" to "CHINESE",
            "Other" to "OTHER",
        )
    }
}

internal class SaleFilter : CheckBoxFilter("On Sale", "sale")

internal class ChapterFilter : RangeFilter("Chapter", "ch")

internal class SortFilter : SelectFilter("Sort", "sort", state) {
    companion object {
        val state = mapOf(
            "Recently Updated" to "updated",
            "Most Bookmarked" to "popular",
            "Most Viewed" to "views",
            "Longest" to "longest",
            "Trending" to "trending",
            "Top Rated" to "rating",
            "Newest" to "newest",
        )
    }
}
