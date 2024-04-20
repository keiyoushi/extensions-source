package eu.kanade.tachiyomi.extension.pt.galaxscanlator

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GalaxScanlator : ZeistManga(
    "GALAX Scans",
    "https://galaxscanlator.blogspot.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(5, 2, TimeUnit.SECONDS)
        .build()

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/search/label/Mangá?max-results=150")

    override val popularMangaSelector = ".blog-posts > section"
    override val popularMangaSelectorTitle: String = "h2"
    override val popularMangaSelectorUrl = "div > a"

    override val mangaDetailsSelector = ".grid.gta-series"
    override val mangaDetailsSelectorGenres = "aside > div > dl dt:contains(Genre) + dd > a[rel=tag]"

    override val useNewChapterFeed = true
    override val chapterCategory = "Capítulo"
    override val pageListSelector = "#reader"
}
