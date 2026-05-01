package eu.kanade.tachiyomi.extension.en.mangamob

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    fun selectedValue() = options[state].second
}

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            "All Genres" to "",
            "Action" to "Action",
            "Adventure" to "Adventure",
            "Comedy" to "Comedy",
            "Cooking" to "Cooking",
            "Drama" to "Drama",
            "Fantasy" to "Fantasy",
            "Gender bender" to "Gender bender",
            "Harem" to "Harem",
            "Historical" to "Historical",
            "Horror" to "Horror",
            "Isekai" to "Isekai",
            "Josei" to "Josei",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
            "Manga" to "Manga",
            "Martial arts" to "Martial arts",
            "Mature" to "Mature",
            "Mecha" to "Mecha",
            "Medical" to "Medical",
            "Mystery" to "Mystery",
            "One shot" to "One shot",
            "Psychological" to "Psychological",
            "Romance" to "Romance",
            "School life" to "School life",
            "Sci fi" to "Sci fi",
            "Seinen" to "Seinen",
            "Shoujo" to "Shoujo",
            "Shounen" to "Shounen",
            "Slice of life" to "Slice of life",
            "Sports" to "Sports",
            "Supernatural" to "Supernatural",
            "Thriller" to "Thriller",
            "Tragedy" to "Tragedy",
            "Webtoons" to "Webtoons",
            "ladies" to "ladies",
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            "Updated" to "Updated",
            "New" to "New",
            "Views" to "Views",
            "Random" to "Random",
        ),
        "Views",
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
        ),
    )
