package eu.kanade.tachiyomi.extension.id.holotoon

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter :
    Filter.Select<String>(
        "Urutkan",
        arrayOf("Terbaru", "Populer", "Rating", "A-Z"),
    ) {
    val value get() = arrayOf("latest", "popular", "rating", "az")[state]
}

internal class TypeFilter :
    Filter.Select<String>(
        "Tipe",
        arrayOf("Semua", "Manga", "Manhwa", "Manhua", "Comic", "Webtoon"),
    ) {
    val value get() = arrayOf("", "manga", "manhwa", "manhua", "comic", "webtoon")[state]
}

internal class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Ongoing", "Completed", "Hiatus"),
    ) {
    val value get() = arrayOf("", "ongoing", "completed", "hiatus")[state]
}

internal class GenreFilter(private val genres: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Genre",
        (listOf("Semua") + genres.map { it.first }).toTypedArray(),
    ) {
    val value get() = if (state == 0) "" else genres[state - 1].second
}
