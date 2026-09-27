package eu.kanade.tachiyomi.extension.en.sirenscans

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.nodes.Document

class StatusFilter :
    Filter.Select<String>(
        "Status",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        )
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "Latest" to "latest",
            "Trending" to "trending",
            "Popular" to "popular",
            "Most Viewed" to "views",
            "Top Rated" to "rating",
            "A-Z" to "az",
            "Z-A" to "za",
        )
    }
}
class TypeFilter :
    Filter.Select<String>(
        "Type",
        ENTRIES.map { it.first }.toTypedArray(),
    ) {
    val selected: String get() = ENTRIES[state].second

    companion object {
        private val ENTRIES = arrayOf(
            "All" to "",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Mangatoon" to "mangatoon",
            "Manga" to "manga",
            "Novel" to "novel",
        )
    }
}

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<GenreCheckBox>(
        "Genres",
        genres.map { GenreCheckBox(it.name, it.value) },
    )

class Genre(val name: String, val value: String)

fun parseGenreData(document: Document): JsonElement {
    val genres = document.select("div#search-genres-list a.genre-tag[data-tag]").map { el ->
        buildJsonObject {
            put("name", el.text().trim().replaceFirstChar { it.uppercase() })
            put("slug", el.attr("data-tag"))
        }
    }
    return buildJsonObject { put("genres", JsonArray(genres)) }
}

fun getGenreList(data: JsonElement? = null): List<Genre> {
    val items = data
        ?.let { runCatching { it.parseAs<GenreResponseDto>() }.getOrNull() }
        ?.genres
        .orEmpty()

    return items.map { item -> Genre(name = item.name, value = item.slug) }
}
