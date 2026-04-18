package eu.kanade.tachiyomi.extension.pt.apecomics

import eu.kanade.tachiyomi.multisrc.mangawork.MangaWork

class Capitoons :
    MangaWork(
        name = "Capitoons",
        baseUrl = "https://capitoons.com",
        lang = "pt-BR",
    ) {
    override val id: Long = 4475020039832513819

    override fun getOrderFilterOptions() = CapitoonsFilters.orderFilterOptions

    override fun getStatusFilterOptions() = CapitoonsFilters.statusFilterOptions

    override fun getTypeFilterOptions() = CapitoonsFilters.typeFilterOptions

    override fun getGenreFilterOptions() = CapitoonsFilters.genreFilterOptions

    override fun getYearFilterOptions() = CapitoonsFilters.yearFilterOptions
}
