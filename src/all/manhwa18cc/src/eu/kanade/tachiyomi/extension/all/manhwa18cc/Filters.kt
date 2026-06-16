package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.source.model.Filter

abstract class Manhwa18SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.map { it.second }.indexOf(defaultValue).coerceAtLeast(0),
) {
    val selected get() = options[state].second.takeIf { it.isNotBlank() }
}

class Manhwa18StatusFilter(title: String) :
    Manhwa18SelectFilter(
        name = title,
        options = listOf(
            "All" to "",
            "Completed" to "completed",
        ),
    )

class Manhwa18OrderFilter(title: String) :
    Manhwa18SelectFilter(
        name = title,
        options = listOf(
            "None" to "",
            "Latest" to "latest",
            "Alphabet" to "alphabet",
            "Rating" to "rating",
            "Trending" to "trending",
        ),
    )

class Manhwa18GenreFilter(title: String) :
    Manhwa18SelectFilter(
        name = title,
        options = listOf(
            "None" to "",
            "Adult (18+)" to "adult",
            "Action" to "action",
            "Adventure" to "adventure",
            "BL" to "bl",
            "Comedy" to "comedy",
            "Comics" to "comics",
            "Doujinshi" to "doujinshi",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Family" to "family",
            "Fantasy" to "fantasy",
            "Gender Bender" to "gender-bender",
            "GL" to "gl",
            "Harem" to "harem",
            "Hentai" to "hentai",
            "Historical" to "historical",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Magic" to "magic",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mecha" to "mecha",
            "Mystery" to "mystery",
            "NTR" to "ntr",
            "Psychological" to "psychological",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Smut" to "smut",
            "Sports" to "sports",
            "Supernatural" to "supernatural",
            "Thriller" to "thriller",
            "Tragedy" to "tragedy",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
        ),
    )
