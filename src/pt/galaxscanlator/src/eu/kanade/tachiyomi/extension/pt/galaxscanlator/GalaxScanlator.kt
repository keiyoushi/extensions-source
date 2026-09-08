package eu.kanade.tachiyomi.extension.pt.galaxscanlator

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class GalaxScanlator : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(5, 2.seconds)

    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h4"
    override val popularMangaSelectorUrl = "a"

    override val mangaDetailsSelector = ".grid.gta-series"
    override val mangaDetailsSelectorGenres = "dt:contains(Genre) + dd a[rel=tag]"

    override val useNewChapterFeed = true
    override val chapterCategory = "Chapter"
    override val pageListSelector = ".separator"

    override fun pageListParse(document: Document) = super.pageListParse(document.selectFirst("#zeist-raw-data")!!.text().asJsoup(baseUrl))

    override val hasFilters = true
    override val hasLanguageFilter = false
}
