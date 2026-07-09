package eu.kanade.tachiyomi.extension.pt.apecomics

import eu.kanade.tachiyomi.multisrc.mangawork.MangaWork
import keiyoushi.annotation.Source

@Source
abstract class Capitoons : MangaWork() {

    override fun getOrderFilterOptions() = orderFilterOptions

    override fun getStatusFilterOptions() = statusFilterOptions

    override fun getTypeFilterOptions() = typeFilterOptions

    override fun getGenreFilterOptions() = genreFilterOptions

    override fun getYearFilterOptions() = yearFilterOptions
}
