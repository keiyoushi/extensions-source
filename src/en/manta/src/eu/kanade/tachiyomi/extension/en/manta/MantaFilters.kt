package eu.kanade.tachiyomi.extension.en.manta

import eu.kanade.tachiyomi.source.model.Filter

private val categories = arrayOf(
    "",
    "New",
    "Exclusive",
    "Completed",
    "Romance",
    "BL / GL",
    "Drama",
    "Fantasy",
    "Thriller",
    "Slice of life",
)

class Category(
    values: Array<String> = categories,
) : Filter.Select<String>("Category", values)

inline val List<Filter<*>>.category: String
    get() = (firstOrNull() as? Category)?.run { values[state] } ?: ""
