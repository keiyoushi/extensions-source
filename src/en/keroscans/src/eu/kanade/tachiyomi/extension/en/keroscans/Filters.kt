package eu.kanade.tachiyomi.extension.en.keroscans

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class FilterData(
    val genres: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val years: Map<String, String> = emptyMap(),
    private val version: Int = 0,
) {
    val outdated: Boolean
        get() = version < CURRENT_VERSION

    companion object {
        const val CURRENT_VERSION = 2
    }
}

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class CheckBoxFilter(name: String, val id: String) :
    Filter.CheckBox(name),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        builder.addQueryParameter(id, "$state")
    }
}

open class MultiSelectFilter(name: String, val id: String, mapping: Map<String, String>) :
    Filter.Group<CheckBoxFilter>(name, mapping.map { CheckBoxFilter(it.key, it.value) }),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        for (checkbox in state) {
            if (checkbox.state) {
                builder.addQueryParameter("$id[]", checkbox.id)
            }
        }
    }
}

open class SelectFilter(name: String, val id: String, val mapping: Map<String, String>) :
    Filter.Select<String>(name, mapping.keys.toTypedArray()),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        this@SelectFilter.mapping[values[state]]?.takeIf { it.isNotEmpty() }?.let { builder.addQueryParameter(id, it) }
    }
}

internal class GenreFilter(mapping: Map<String, String>) : MultiSelectFilter("Genres", "genre", mapping)

internal class StatusFilter : SelectFilter("Status", "status", mapping) {
    companion object {
        val mapping = mapOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        )
    }
}

internal class DemographicFilter : SelectFilter("Demographic", "demographic", mapping) {
    companion object {
        val mapping = mapOf(
            "Any" to "",
            "Josei" to "josei",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
        )
    }
}

internal class TagFilter(mapping: Map<String, String>) : MultiSelectFilter("Tags", "tag", mapping)

internal class YearFilter(mapping: Map<String, String>) : SelectFilter("Release Year", "year", mapping)

internal class FormatFilter : SelectFilter("Format", "format", mapping) {
    companion object {
        val mapping = mapOf(
            "Any" to "",
            "Serialized" to "Serialized",
            "One-Shot" to "one_shot",
        )
    }
}

internal class RatingFilter : SelectFilter("Content Rating", "rating", mapping) {
    companion object {
        val mapping = mapOf(
            "Any" to "",
            "Unrated" to "unrated",
            "All Ages" to "all_ages",
            "Teen" to "teen",
            "Mature" to "mature",
            "Adult" to "adult",
        )
    }
}

internal class SortFilter : SelectFilter("Sort", "sort", mapping) {
    companion object {
        val mapping = mapOf(
            "Latest updated" to "latest",
            "Recently added" to "added",
            "Most popular" to "popular",
            "Title A-Z" to "title_asc",
            "Title Z-A" to "title_desc",
            "Release year" to "year",
        )
    }
}
