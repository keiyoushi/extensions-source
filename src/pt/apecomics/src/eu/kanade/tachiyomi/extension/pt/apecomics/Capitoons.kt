package eu.kanade.tachiyomi.extension.pt.apecomics

import eu.kanade.tachiyomi.multisrc.mangawork.MangaWork

class Capitoons :
    MangaWork(
        name = "Capitoons",
        baseUrl = "https://capitoons.com",
        lang = "pt-BR",
    ) {
    override val id: Long = 4475020039832513819

    override fun getOrderFilterOptions() = orderFilterOptions

    override fun getStatusFilterOptions() = statusFilterOptions

    override fun getTypeFilterOptions() = typeFilterOptions

    override fun getGenreFilterOptions() = genreFilterOptions

    override fun getYearFilterOptions() = yearFilterOptions
}
