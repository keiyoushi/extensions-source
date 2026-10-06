package eu.kanade.tachiyomi.extension.vi.jellycomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class JellyComics : Madara() {
    override val mangaSubString = "truyen"
    override val genreDirectory = "the-loai"
    override val supportsPostId = false

    override fun archiveSelector() = ".comic-list-item"
    override val archiveUrlSelector = "a.comic-block-link"
    override val archiveTitleSelector = "h3.comic-block-title"

    override val mangaDetailsSelectorTitle = "h2.comic-title"
    override val mangaDetailsSelectorThumbnail = ".comic-desc-img"
    override val mangaDetailsSelectorStatus = "li:has(strong:containsOwn(Trạng thái))"
    override val mangaDetailsSelectorGenre = "li:has(strong:containsOwn(Thể loại)) a"
    override val mangaDetailsSelectorTag = "li:has(strong:containsOwn(Thẻ)) a"
    override val altNameSelector = "li:has(strong:containsOwn(Tên khác))"

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-bg") -> element.attr("abs:data-bg")
        else -> super.imageFromElement(element)
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        author = document.selectFirst("li:has(strong:containsOwn(Tác giả))")
            ?.ownText()?.trim()
            ?.takeIf { it.isNotEmpty() && !isUpdating(it) }
    }

    override fun chapterListSelector() = "li.wp-manga-chapter, div.chapter-item"
    override val chapterNameSelector = "p.chapter-title"
    override val chapterDateSelector = "span.chapter-release-date, p.chapter-meta i:last-child"

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        if (chapterMode != ChapterMode.MangaAjax) {
            return super.fetchChapters(mangaPath, id, mangaPage)
        }
        // Chapters are embedded in the manga page as .chapter-item divs with
        // correct URLs. Fall back to the AJAX endpoint if the page has none.
        val document = mangaPage ?: client.get("$baseUrl$mangaPath").asJsoup()
        parseChapterList(document, mangaPath).takeIf { it.isNotEmpty() }?.let { return it }
        // Fall back to the AJAX endpoint if the page has no embedded chapters.
        var url = "$baseUrl${mangaPath.trimEnd('/')}/ajax/chapters/"
        var finalPath = mangaPath
        repeat(5) {
            val response = noRedirectClient.post(url, xhrHeaders, FormBody.Builder().build())
            if (response.isRedirect) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) return emptyList()
                url = location
                finalPath = location.toHttpUrl().encodedPath.substringBefore("/ajax/chapters/")
            } else {
                return parseChapterList(response.asJsoup(), finalPath)
            }
        }
        return emptyList()
    }

    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)

    override fun parsePages(document: Document): List<Page> = document.select(".reading-content img.manga-page").mapIndexedNotNull { index, img ->
        imageFromElement(img)?.let { Page(index, document.location(), it) }
    }

    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).build()
    }
}
