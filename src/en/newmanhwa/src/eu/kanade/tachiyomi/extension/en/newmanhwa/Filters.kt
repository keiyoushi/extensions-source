package eu.kanade.tachiyomi.extension.en.newmanhwa

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "Hiatus"),
    )

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Updated", "Popular", "Most Chapters", "Newest", "A-Z", "Z-A"),
        0,
    )

class Genre(val name: String, val value: String) {
    override fun toString(): String = name
}

class GenreFilter(genres: List<Genre>) :
    Filter.Select<Genre>(
        "Genre",
        (listOf(Genre("All", "")) + genres).toTypedArray(),
    )

@Serializable
data class GenreDto(val name: String, val slug: String)

@Serializable
data class GenreResponseDto(val genres: List<GenreDto> = emptyList())

fun getGenreList(data: JsonElement? = null): List<Genre> {
    val items = data
        ?.let { runCatching { it.parseAs<GenreResponseDto>() }.getOrNull() }
        ?.genres
        .orEmpty()

    return items.map { item ->
        Genre(name = item.name, value = item.slug)
    }
}
