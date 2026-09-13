package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(genres: List<Genre>, excludedIds: Set<String> = emptySet()) :
    Filter.Group<Filter.TriState>(
        "Genres",
        genres.map { genre ->
            val state = if (genre.id in excludedIds) {
                Filter.TriState.STATE_EXCLUDE
            } else {
                Filter.TriState.STATE_IGNORE
            }
            object : Filter.TriState(genre.name, state) {}
        },
    ) {
    val genreIds = genres.map { it.id }
}

// Split large list separate Groups.
internal class TagFilters(tags: List<Tag>) :
    Filter.Group<TagFilter>(
        "Tags",
        tags.sortedBy { it.name }.groupBy {

            val c = it.name.firstOrNull()?.uppercase()

            when {
                c == null || c !in "A".."Z" -> "0-9"
                else -> c
            }
        }.map { (letters, tagsChunk) ->
            TagFilter(letters, tagsChunk)
        },
    )

internal class TagFilter(letters: String, tags: List<Tag>) :
    Filter.Group<Filter.TriState>(
        letters,
        tags.map { tag ->
            object : Filter.TriState(tag.name) {}
        },
    ) {
    val tagIds = tags.map { it.id }
}

internal class TypeFilter(types: List<Type>) :
    Filter.Group<Filter.CheckBox>(
        "Manga Type",
        types.map { type ->
            object : Filter.CheckBox(type.name, false) {}
        },
    ) {
    val ids = types.map { it.id }
}

internal class StatusFilter(statuses: List<Status>) :
    Filter.Group<Filter.CheckBox>(
        "Publishing Status",
        statuses.map { status ->
            object : Filter.CheckBox(status.name, false) {}
        },
    ) {
    val ids = statuses.map { it.id }
}

internal class YearFilter : Filter.Text("Year (e.g., 2024)")

internal class MinChaptersFilter : Filter.Text("Minimum Chapters")

internal class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Popularity", "Trending", "Date Added", "Release Date", "Top Rated", "Title"),
        Selection(0, false),
    ) {
    companion object {
        val VALUES = arrayOf("views", "trending", "dateAdded", "released", "mbRating", "title")
    }
}

internal class AdultFilter(state: Boolean) : Filter.CheckBox("Show Adult Content", state)

internal class OfficialFilter : Filter.CheckBox("Only Official Translations", false)

internal data class Genre(val name: String, val id: String)

internal data class Tag(val name: String, val id: String)

internal data class Type(val name: String, val id: String)

internal data class Status(val name: String, val id: String)
