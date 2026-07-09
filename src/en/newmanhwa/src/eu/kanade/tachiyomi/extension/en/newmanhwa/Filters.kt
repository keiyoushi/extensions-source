package eu.kanade.tachiyomi.extension.en.newmanhwa

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "Hiatus"),
    )

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All", "Action", "Drama", "Ecchi", "Fantasy", "Harem", "Historical",
            "Martial Arts", "Mature", "Mystery", "Psychological", "Romance", "School Life",
        ),
    )

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Updated", "Popular", "Most Chapters", "Newest", "A-Z", "Z-A"),
        0,
    )
