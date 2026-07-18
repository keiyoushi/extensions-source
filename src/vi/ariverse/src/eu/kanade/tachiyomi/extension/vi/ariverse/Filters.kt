package eu.kanade.tachiyomi.extension.vi.ariverse

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class GenreFilter(
    name: String,
    genres: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf(
            "All",
            "Ongoing",
            "Completed",
        ),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> null
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf(
            "Relevance",
            "Latest update",
            "Newest published",
            "Title A–Z",
            "Title Z–A",
            "Recently added",
        ),
    ) {
    fun toSortValue(): String? = when (state) {
        1 -> "updated_at"
        2 -> "published_at"
        3 -> "title"
        4 -> "title"
        5 -> "created_at"
        else -> null
    }

    fun toOrderValue(): String? = when (state) {
        4 -> "asc"
        else -> if (toSortValue() != null) "desc" else null
    }
}

fun buildGenreFilter(data: JsonElement?): FilterList {
    val genres = (data as? JsonArray)
        ?.mapNotNull {
            val obj = it.jsonObject
            val name = obj["name"]?.string ?: return@mapNotNull null
            val slug = obj["slug"]?.string ?: return@mapNotNull null
            name to slug
        }
        .orEmpty()

    return FilterList(
        GenreFilter("Genre", genres),
        StatusFilter(),
        SortFilter(),
    )
}
