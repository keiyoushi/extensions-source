package eu.kanade.tachiyomi.extension.en.mangatown

import eu.kanade.tachiyomi.source.model.Filter

open class MethodFilter(name: String) :
    Filter.Select<String>(
        name,
        METHODS.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = METHODS[state].second

    companion object {
        private val METHODS = arrayOf(
            "Contains" to "cw",
            "Begins with" to "bw",
            "Ends with" to "ew",
        )
    }
}

class TitleMethodFilter : MethodFilter("Title Method")

class AuthorFilter : Filter.Text("Author")

class AuthorMethodFilter : MethodFilter("Author Method")

class ArtistFilter : Filter.Text("Artist")

class ArtistMethodFilter : MethodFilter("Artist Method")

class DemographicFilter :
    Filter.Select<String>(
        "Demographic",
        DEMOGRAPHICS.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = DEMOGRAPHICS[state].second

    companion object {
        private val DEMOGRAPHICS = arrayOf(
            "Show All" to "",
            "Shounen" to "SHOUNEN",
            "Seinen" to "SEINEN",
            "Shoujo" to "SHOUJO",
            "Yaoi" to "YAOI",
            "Shoujo Ai" to "SHOUJO AI",
            "Josei" to "JOSEI",
            "Shounen Ai" to "SHOUNEN AI",
            "Yuri" to "YURI",
        )
    }
}

class Genre(name: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<Genre>(
        "Genres",
        GENRES.map(::Genre),
    ) {
    companion object {
        private val GENRES = arrayOf(
            "4 Koma",
            "Action",
            "Adventure",
            "Comedy",
            "Cooking",
            "Doujinshi",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Gender Bender",
            "Harem",
            "Historical",
            "Horror",
            "Martial Arts",
            "Mature",
            "Mecha",
            "Music",
            "Mystery",
            "One Shot",
            "Psychological",
            "Reverse Harem",
            "Romance",
            "School Life",
            "Sci Fi",
            "Shotacon",
            "Slice Of Life",
            "Smut",
            "Sports",
            "Supernatural",
            "Suspense",
            "Tragedy",
            "Vampire",
            "Webtoons",
            "Youkai",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        STATUSES.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = STATUSES[state].second

    companion object {
        private val STATUSES = arrayOf(
            "Show All" to "",
            "Completed" to "1",
            "Ongoing" to "0",
        )
    }
}

class ReleasedFilter : Filter.Text("Year of Released (e.g. 2020)")

class ReleasedMethodFilter :
    Filter.Select<String>(
        "Year of Released Method",
        RELEASED_METHODS.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = RELEASED_METHODS[state].second

    companion object {
        private val RELEASED_METHODS = arrayOf(
            "On" to "eq",
            "Before" to "lt",
            "After" to "gt",
        )
    }
}

class RatingFilter :
    Filter.Select<String>(
        "Rating",
        RATINGS.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = RATINGS[state].second

    companion object {
        private val RATINGS = arrayOf(
            "Any star" to "",
            "1 star" to "1",
            "2 stars" to "2",
            "3 stars" to "3",
            "4 stars" to "4",
            "5 stars" to "5",
        )
    }
}

class RatingMethodFilter :
    Filter.Select<String>(
        "Rating Method",
        RATING_METHODS.map { it.first }.toTypedArray(),
    ) {
    val selected: String
        get() = RATING_METHODS[state].second

    companion object {
        private val RATING_METHODS = arrayOf(
            "Is" to "eq",
            "Less than" to "lt",
            "More than" to "gt",
        )
    }
}
