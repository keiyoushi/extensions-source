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

abstract class SoloManga : KeiSource() {

    override val supportsLatest = false

    protected abstract val mangaTitle: String

    protected abstract val chapterSlug: String

    protected open val chapterRegex by lazy { Regex("""$chapterSlug(\d+)/?$""") }

    protected open val chapterListSelector = "#ceo_latest_comics_widget-3"

    protected abstract val imageHosts: List<String>

    protected open val pageSelector = ".entry-content img"

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = mangaTitle
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(singleManga()), false)

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = listOf(singleManga()).filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    // ============================== Details ===============================

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

    private fun mangaDetailsParse(document: Document): SManga = singleManga().apply {
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    // ============================== Chapters ==============================

    protected open fun chapterListParse(document: Document): List<SChapter> = document.select("$chapterListSelector a[href*='$chapterSlug']")
        .map { it.attr("abs:href") }
        .mapNotNull { href -> chapterRegex.find(href)?.let { chapterFromMatch(href, it) } }
        .distinctBy { it.name }

    protected open fun chapterFromMatch(href: String, match: MatchResult): SChapter = SChapter.create().apply {
        val number = match.groupValues[1]
        setUrlWithoutDomain(href)
        name = "Chapter $number"
        chapter_number = number.toFloatOrNull() ?: 0f
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(pageSelector)
            .mapNotNull { img ->
                // Pages are lazy-loaded: src holds a data: placeholder, the real image is in data-src or srcset
                val url = img.attr("data-src")
                    .ifBlank { img.attr("data-lazy-src") }
                    .ifBlank { img.attr("srcset").substringBefore(',').substringBefore(' ').trim() }
                    .ifBlank { img.attr("abs:src") }
                url.takeIf { it.startsWith("http") && isPageImage(it) }
            }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun isPageImage(url: String): Boolean {
        if ("cropped-" in url) return false // site logo and other cropped assets
        return (bloggerHosts + imageHosts).any { it in url }
    }

    private val bloggerHosts = listOf("blogger.googleusercontent.com", "bp.blogspot.com")
}
