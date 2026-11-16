package eu.kanade.tachiyomi.extension.all.jellyfin

import eu.kanade.tachiyomi.source.model.Filter

open class IdSelect(name: String, private val options: List<NamedId>, first: String) : Filter.Select<String>(name, (listOf(first) + options.map { it.name }).toTypedArray()) {
    val selectedId get() = options.getOrNull(state - 1)?.id
}

class LibraryFilter(libraries: List<NamedId>) : IdSelect("Library", libraries, "All")

class CollectionFilter(collections: List<NamedId>) : IdSelect("Collection", collections, "None")

class SortFilter :
    Filter.Sort(
        "Sort",
        SORTS.map { it.name }.toTypedArray(),
        Selection(0, true),
    ) {
    private val selected get() = SORTS[state?.index ?: 0]
    val sortBy get() = selected.sortBy
    val byBooks get() = selected.byBooks
    val sortOrder get() = if (state?.ascending != false) "Ascending" else "Descending"
}

private class SortOption(val name: String, val sortBy: String, val byBooks: Boolean = false)

// Release date, play date and rating are mostly on books, so those sort books and folders
private val SORTS = listOf(
    SortOption("Name", "SortName"),
    SortOption("Date added", "DateCreated"),
    SortOption("Release date", "PremiereDate", byBooks = true),
    SortOption("Date read", "DatePlayed", byBooks = true),
    SortOption("Rating", "CommunityRating", byBooks = true),
    SortOption("Random", "Random"),
)

class StatusFilter : Filter.Select<String>("Status", STATUSES.map { it.first }.toTypedArray()) {
    val value get() = STATUSES[state].second
}

private val STATUSES = listOf(
    "Any" to null,
    "Unread" to "IsUnplayed",
    "Read" to "IsPlayed",
    "In progress" to "IsResumable",
    "Favorite" to "IsFavorite",
)

class OptionCheckBox(name: String, val value: String) : Filter.CheckBox(name)

open class OptionGroup(name: String, val parameter: String, val separator: String, options: List<Pair<String, String>>) : Filter.Group<OptionCheckBox>(name, options.map { OptionCheckBox(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

class GenreFilter(genres: List<String>) : OptionGroup("Genres", "Genres", "|", genres.map { it to it })

class TagFilter(tags: List<String>) : OptionGroup("Tags", "Tags", "|", tags.map { it to it })

class PeopleFilter(people: List<NamedId>) : OptionGroup("Authors and artists", "PersonIds", ",", people.map { it.name to it.id })

class StudioFilter(studios: List<NamedId>) : OptionGroup("Publishers", "StudioIds", ",", studios.map { it.name to it.id })

class RatingFilter(ratings: List<String>) : OptionGroup("Parental ratings", "OfficialRatings", "|", ratings.map { it to it })

class YearFilter(years: List<Int>) : OptionGroup("Years", "Years", ",", years.map { "$it" to "$it" })
