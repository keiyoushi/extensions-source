package eu.kanade.tachiyomi.extension.pt.apecomics

object CapitoonsFilters {
    val orderFilterOptions = arrayOf(
        "A-Z" to "title",
        "Z-A" to "titlereverse",
        "Latest Update" to "update",
        "Latest Added" to "latest",
        "Popular" to "popular",
    )

    val statusFilterOptions = arrayOf(
        "All" to "",
        "Publishing" to "Publishing",
        "Finished" to "Finished",
    )

    val typeFilterOptions = arrayOf(
        "All" to "",
        "Manga" to "Manga",
        "Manhwa" to "Manhwa",
        "Manhua" to "Manhua",
    )

    val genreFilterOptions = arrayOf(
        "Ação" to "acao",
        "Artes Marciais" to "artes-marciais",
        "Cultivo" to "cultivo",
        "Ecchi" to "ecchi",
        "Fantasia" to "fantasia",
        "Harem" to "harem",
        "Jianghu" to "jianghu",
    )

    val yearFilterOptions = arrayOf(
        "2026" to "2026",
    )
}
