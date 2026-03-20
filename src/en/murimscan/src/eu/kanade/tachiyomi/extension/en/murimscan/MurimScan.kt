package eu.kanade.tachiyomi.extension.en.murimscan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import org.jsoup.nodes.Document

class MurimScan : ZeistManga("MurimScan", "https://www.murimscans.site", "en") {

    // Madara -> ZeistManga
    override val versionId = 2

    // Popular
    override val popularMangaSelector = ".PopularPosts article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".post-title a"

    // Details
    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "dl.flex:contains(Genre) a[rel=tag], dl.flex:contains(Type) a[rel=tag]"
    override val mangaDetailsSelectorInfo = "dl.flex"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    // Pages
    override val pageListSelector = ".post-body, .check-box"

    override fun popularMangaParse(response: Response): MangasPage = super.popularMangaParse(response).apply {
        mangas.forEach { it.url = it.url.substringBefore("?") }
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val label = doc.selectFirst("meta[property='og:title']")?.attr("content")
            ?: return super.getChapterFeedUrl(doc)

        return apiUrl(chapterCategory).apply {
            addPathSegment(label)
            addQueryParameter("max-results", "999999")
        }.build().toString()
    }

    override val hasFilters = true
}
