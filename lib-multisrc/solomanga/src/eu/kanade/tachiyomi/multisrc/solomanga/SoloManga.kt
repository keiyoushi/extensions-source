package eu.kanade.tachiyomi.multisrc.solomanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class SoloManga : KeiSource() {

    override val supportsLatest = false

    protected abstract val mangaTitle: String

    protected open val chapterListSelector = "#ceo_latest_comics_widget-3 a"

    protected open val imageHosts = emptyList<String>()

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = mangaTitle
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(singleManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = listOf(singleManga()).filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return singleManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document),
            chapters = chapterListParse(document),
        )
    }

    protected open fun mangaDetailsParse(document: Document): SManga = singleManga().apply {
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    protected open fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector).map(::chapterFromElement)

    // Slugs can be off (maid-sama-chapter-59-5 is Chapter 60), the link text is right
    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = chapterName(element)
        chapterNumberRegex.find(name)?.let { chapter_number = it.groupValues[1].toFloat() }
    }

    protected open fun chapterName(element: Element): String = element.text()

    private val chapterNumberRegex = Regex("""Chapter\s+(\d+(?:\.\d+)?)""")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // Site domain without its wN. mirror label, covers img.<domain> and <domain>/wp-content/uploads
        val hosts = bloggerHosts + imageHosts + baseUrl.toHttpUrl().host.substringAfter('.')
        return document.select(".entry-content img, .post-single-content img")
            .mapNotNull { img ->
                // Pages are lazy-loaded: src holds a data: placeholder, the real image is in data-src or srcset
                val url = img.attr("data-src")
                    .ifBlank { img.attr("data-lazy-src") }
                    .ifBlank { img.attr("srcset").substringBefore(',').substringBefore(' ').trim() }
                    .ifBlank { img.attr("abs:src") }
                // "cropped-" is the site logo and other cropped assets
                url.takeIf { it.startsWith("http") && "cropped-" !in it && hosts.any(it::contains) }
            }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private val bloggerHosts = listOf("blogger.googleusercontent.com", "bp.blogspot.com")
}
