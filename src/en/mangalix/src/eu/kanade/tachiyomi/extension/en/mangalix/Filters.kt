package eu.kanade.tachiyomi.extension.en.mangalix

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Locale

internal class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUS_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val selected: String?
        get() = STATUS_OPTIONS[state].second.takeIf { it.isNotEmpty() }

    fun matches(status: String): Boolean = selected?.let {
        status.normalizedStatus() == it
    } ?: true

    companion object {
        private val STATUS_OPTIONS = listOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        )
    }
}

internal class SortFilter(
    selection: Selection = Selection(0, false),
) : Filter.Sort(
    "Sort By",
    SORT_OPTIONS.map { it.first }.toTypedArray(),
    selection,
) {
    val selected: String
        get() = SORT_OPTIONS[state?.index ?: 0].second

    val ascending: Boolean
        get() = state?.ascending ?: false

    companion object {
        const val DEFAULT = "default"
        const val LATEST = "latest"
        const val RATING = "rating"
        const val TITLE = "title"
        const val RELEASE_YEAR = "release_year"

        private val SORT_OPTIONS = listOf(
            "Default" to DEFAULT,
            "Latest Update" to LATEST,
            "Release Year" to RELEASE_YEAR,
            "Rating" to RATING,
            "Title" to TITLE,
        )
    }
}

internal class GenreFilter :
    Filter.Group<GenreOption>(
        "Genres",
        GENRES.map(::GenreOption),
    ) {
    val selectedGenres: Set<String>
        get() = state.filter { it.state }.mapTo(linkedSetOf()) { it.name }

    fun matches(genres: List<String>): Boolean {
        val selected = selectedGenres.mapTo(hashSetOf()) { it.normalizedGenre() }
        if (selected.isEmpty()) return true

        val available = genres.mapTo(hashSetOf()) { it.normalizedGenre() }
        return available.containsAll(selected)
    }

    companion object {
        private val GENRES = listOf(
            "4-Koma",
            "Action",
            "Adventure",
            "Comedy",
            "Dark Fantasy",
            "Drama",
            "Ecchi",
            "Family",
            "Fantasy",
            "Harem",
            "Historical",
            "Horror",
            "Isekai",
            "Magic",
            "Manhwa",
            "Martial Arts",
            "Mature",
            "Mecha",
            "Military",
            "Murim",
            "Mystery",
            "Parody",
            "Psychological",
            "Regression",
            "Reincarnation",
            "Romance",
            "School Life",
            "Sci-Fi",
            "Seinen",
            "Shoujo",
            "Shounen",
            "Slice of Life",
            "Sports",
            "Supernatural",
            "Survival",
            "System",
            "Thriller",
            "Tragedy",
            "Vampire",
            "Webtoon",
        )
    }
}

internal class GenreOption(name: String) : Filter.CheckBox(name)

private fun String.normalizedStatus(): String = when (normalizedKey()) {
    "ongoing", "publishing", "releasing", "active" -> "ongoing"
    "completed", "complete", "finished" -> "completed"
    "hiatus", "onhiatus", "paused" -> "hiatus"
    else -> normalizedKey()
}

private fun String.normalizedGenre(): String = when (val value = normalizedKey()) {
    "school" -> "schoollife"
    "shojo" -> "shoujo"
    "shonen" -> "shounen"
    "webtoons" -> "webtoon"
    else -> value
}

private fun String.normalizedKey(): String = lowercase(Locale.ROOT)
    .filter(Char::isLetterOrDigit)
