package eu.kanade.tachiyomi.extension.pt.galaxscanlator

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class GalaxScanlator : ZeistManga() {
    override val client = super.client.newBuilder()
        .rateLimit(5, 2.seconds)
        .build()

    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3"
    override val popularMangaSelectorUrl = "a"

    override val mangaDetailsSelector = ".grid.gta-series"
    override val mangaDetailsSelectorGenres = "dt:contains(Genre) + dd a[rel=tag]"

    override val useNewChapterFeed = true
    override val chapterCategory = "Capítulo"
    override val pageListSelector = "#reader"

    override val hasFilters = true
    override val hasLanguageFilter = false
}
