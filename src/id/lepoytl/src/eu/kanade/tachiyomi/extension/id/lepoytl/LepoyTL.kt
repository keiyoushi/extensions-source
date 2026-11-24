package eu.kanade.tachiyomi.extension.id.lepoytl

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class LepoyTL : ZeistManga() {

    override val popularMangaSelector = "div.PopularPosts div.grid > article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "h3 > a"

    // =========================== Manga Details ============================
    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "aside dl a[rel=tag]"
    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    // =============================== Pages ================================
    override val pageListSelector = "#reader"

    // ============================== Filters ===============================
    override val hasFilters = true
    override val hasLanguageFilter = false

    // Every series is tagged "Manga"
    override val hasTypeFilter = false

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
    )

    override fun getGenreList() = listOf(
        Genre("Animals", "Animals"),
        Genre("Comedy", "Comedy"),
        Genre("Crossdress", "Crossdress"),
        Genre("Drama", "Drama"),
        Genre("Gender Bender", "Gender Bender"),
        Genre("Genderswap", "Genderswap"),
        Genre("Psychological", "Psychological"),
        Genre("Reincarnation", "Reincarnation"),
        Genre("Romance", "Romance"),
        Genre("School Life", "School Life"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Supernatural", "Supernatural"),
        Genre("Tragedy", "Tragedy"),
    )
}
