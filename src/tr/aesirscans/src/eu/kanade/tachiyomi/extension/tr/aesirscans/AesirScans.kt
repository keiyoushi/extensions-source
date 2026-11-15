package eu.kanade.tachiyomi.extension.tr.aesirscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class AesirScans : ZeistManga() {

    // The site's popular posts widget fails to render
    override val supportsLatest = false

    override val mangaDetailsSelector = "article.hentry"
    override val mangaDetailsSelectorThumbnail = ".entry-content .separator img"
    override val mangaDetailsSelectorDescription = ".synopsis"
    override val mangaDetailsSelectorGenres = "h1.entry-title + div > span:first-child, a[rel=tag]"
    override val mangaDetailsSelectorStatus = "h1.entry-title + div > span:last-child"
    override val mangaDetailsSelectorAltName = ".infotable .fmed:has(b:matchesOwn(^Orijinal isim$)) span"
    override val mangaDetailsSelectorInfo = ".infotable .fmed"
    override val mangaDetailsSelectorInfoTitle = "b"
    override val mangaDetailsSelectorInfoDescription = "span"

    override val statusOnGoingList = super.statusOnGoingList + listOf("devam ediyor")
    override val statusCompletedList = super.statusCompletedList + listOf("tamamlandı")
    override val statusCancelledList = super.statusCancelledList + listOf("bırakıldı")

    // Chapter links lead to their series, the post that carries the same label
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (!POST_PATH_REGEX.matches(url.encodedPath)) return null
        val post = getPost(apiUrl().addQueryParameter("path", url.encodedPath)) ?: return null
        val labels = post.category.orEmpty().map { it.term }
        if (chapterCategory !in labels) return post.toSManga(baseUrl)
        val seriesLabel = labels.singleOrNull { it != chapterCategory } ?: return null
        return getPost(apiUrl(SERIES_CATEGORY).addPathSegment(seriesLabel))?.toSManga(baseUrl)
    }

    // The site's chapter list uses the series label: its title without commas and apostrophes
    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String {
        val label = doc.selectFirst("#chapterlist")!!.attr("data-label").replace(LABEL_STRIP_REGEX, "").trim()
        return apiUrl(chapterCategory).addPathSegment(label).build().toString()
    }

    // The reader page writes the post from a script, so the pages come from the post in the feed
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val content = getPost(apiUrl().addQueryParameter("path", chapter.url))!!.content!!.t
        return Jsoup.parseBodyFragment(content, baseUrl).select("img[src]").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    private suspend fun getPost(url: HttpUrl.Builder) = client.get(url.build(), Fetch.XHR).parseAs<ZeistMangaDto>().feed?.entry?.firstOrNull()

    companion object {
        private const val SERIES_CATEGORY = "Series"
        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")
        private val LABEL_STRIP_REGEX = Regex("[',]")
    }
}
