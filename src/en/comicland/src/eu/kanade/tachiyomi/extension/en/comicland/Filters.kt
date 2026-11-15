package eu.kanade.tachiyomi.extension.en.comicland

import eu.kanade.tachiyomi.source.model.Filter

class Filters :
    Filter.Select<String>(
        "Category",
        arrayOf("Recommended", "Official", "Ongoing", "Popular"),
        3,
    ) {
    val selectedEndpoint: String
        get() = when (state) {
            1 -> "/comics/official"
            3 -> "/comics/popular"
            else -> "/comics"
        }

    val selectedStatus: String?
        get() = when (state) {
            2 -> "ongoing"
            else -> null
        }
}
