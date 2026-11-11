package eu.kanade.tachiyomi.extension.en.chikari

import eu.kanade.tachiyomi.source.model.Filter

class Sort(val name: String, val value: String, val period: String? = null)

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.name }.toTypedArray()) {
    val selected get() = SORTS[state]
}

// Trending and Most Bookmarked are counted over a period, the site's default is the week
val SORTS = listOf(
    Sort("Popularity", "popular"),
    Sort("Top Rated", "top_rated"),
    Sort("Complete & Bingeable", "bingeable"),
    Sort("Hidden Gems", "gems"),
    Sort("Trending", "trending-week", "week"),
    Sort("Recently Updated", "updated"),
    Sort("Recently Added", "added"),
    Sort("Most Bookmarked", "most_bookmarked", "week"),
)

class CheckBoxOption(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

open class CheckBoxGroup(name: String, options: List<CheckBoxOption>) : Filter.Group<CheckBoxOption>(name, options) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class TypeFilter :
    CheckBoxGroup(
        "Type",
        listOf(
            CheckBoxOption("Manga", "manga", true),
            CheckBoxOption("Manhwa", "manhwa", true),
            CheckBoxOption("Manhua", "manhua", true),
            CheckBoxOption("OEL", "oel", false),
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "Status",
        listOf(
            CheckBoxOption("Releasing", "releasing", false),
            CheckBoxOption("Completed", "completed", false),
            CheckBoxOption("Hiatus", "hiatus", false),
            CheckBoxOption("Cancelled", "cancelled", false),
            CheckBoxOption("Upcoming", "upcoming", false),
        ),
    )

class ContentRatingFilter :
    CheckBoxGroup(
        "Content rating",
        listOf(
            CheckBoxOption("Safe", "safe", true),
            CheckBoxOption("Suggestive", "suggestive", true),
            CheckBoxOption("Erotica", "erotica", true),
            CheckBoxOption("Pornographic", "pornographic", true),
        ),
    )

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<Genre>(
        "Genres",
        listOf(
            Genre("Action", "action"),
            Genre("Adult", "adult"),
            Genre("Adventure", "adventure"),
            Genre("Award Winning", "award_winning"),
            Genre("Boys Love", "boys_love"),
            Genre("Comedy", "comedy"),
            Genre("Doujinshi", "doujinshi"),
            Genre("Drama", "drama"),
            Genre("Ecchi", "ecchi"),
            Genre("Erotica", "erotica"),
            Genre("Fantasy", "fantasy"),
            Genre("Gender Bender", "gender_bender"),
            Genre("Girls Love", "girls_love"),
            Genre("Gourmet", "gourmet"),
            Genre("Harem", "harem"),
            Genre("Hentai", "hentai"),
            Genre("Historical", "historical"),
            Genre("Horror", "horror"),
            Genre("Josei", "josei"),
            Genre("Lolicon", "lolicon"),
            Genre("Mahou Shoujo", "mahou_shoujo"),
            Genre("Martial Arts", "martial_arts"),
            Genre("Mature", "mature"),
            Genre("Mecha", "mecha"),
            Genre("Music", "music"),
            Genre("Mystery", "mystery"),
            Genre("Psychological", "psychological"),
            Genre("Romance", "romance"),
            Genre("School Life", "school_life"),
            Genre("Sci-Fi", "sci-fi"),
            Genre("Seinen", "seinen"),
            Genre("Shotacon", "shotacon"),
            Genre("Shoujo", "shoujo"),
            Genre("Shoujo Ai", "shoujo_ai"),
            Genre("Shounen", "shounen"),
            Genre("Shounen Ai", "shounen_ai"),
            Genre("Slice of Life", "slice_of_life"),
            Genre("Smut", "smut"),
            Genre("Sports", "sports"),
            Genre("Supernatural", "supernatural"),
            Genre("Suspense", "suspense"),
            Genre("Thriller", "thriller"),
            Genre("Tragedy", "tragedy"),
            Genre("Yaoi", "yaoi"),
            Genre("Yuri", "yuri"),
        ),
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.slug }
    val excluded get() = state.filter { it.isExcluded() }.map { it.slug }
}
