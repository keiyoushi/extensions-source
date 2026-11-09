package eu.kanade.tachiyomi.extension.en.zinmangaart

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(value: String = "views") :
    Filter.Select<String>(
        "Sort by",
        options.map { it.first }.toTypedArray(),
        options.indexOfFirst { it.second == value },
    ) {
    fun toUriPart(): String = options[state].second

    companion object {
        private val options = arrayOf(
            "Relevance" to "",
            "Latest" to "latest",
            "A-Z" to "alphabet",
            "Rating" to "rating",
            "Trending" to "trending",
            "Most Views" to "views",
            "New" to "new-manga",
        )

        val POPULAR = SortFilter("views")
        val LATEST = SortFilter("latest")
    }
}

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Group<GenreFilter.GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.first, it.second) }) {
    class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

    fun selectedGenres(): List<String> = state.filter { it.state }.map { it.slug }
}

class GenreOperatorFilter :
    Filter.Select<String>(
        "Genre matching",
        arrayOf(
            "OR (having one of selected genres)",
            "AND (having all selected genres)",
        ),
    ) {
    fun toUriPart(): String = if (state == 1) "1" else ""
}

class AuthorFilter : Filter.Text("Author")

class ArtistFilter : Filter.Text("Artist")

class ReleaseFilter : Filter.Text("Year of Released")

class AdultFilter :
    Filter.Select<String>(
        "Adult content",
        arrayOf(
            "All",
            "None adult content",
            "Only adult content",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "0"
        2 -> "1"
        else -> ""
    }
}

class StatusFilter :
    Filter.Group<StatusFilter.StatusCheckBox>(
        "Status",
        listOf(
            StatusCheckBox("OnGoing", "ongoing"),
            StatusCheckBox("Completed", "completed"),
            StatusCheckBox("New", "new"),
            StatusCheckBox("Drop", "drop"),
        ),
    ) {
    class StatusCheckBox(name: String, val value: String) : Filter.CheckBox(name)

    fun selectedStatuses(): List<StatusCheckBox> = state.filter { it.state }
}
