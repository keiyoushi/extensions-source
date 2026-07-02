package eu.kanade.tachiyomi.extension.pt.littletyrant

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LittleTyrant : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Site", "same-origin")

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Popular =================================

    override fun popularMangaSelector() = "[id*=manga-item-]"
    override val popularMangaUrlSelector = ".card-title a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst(popularMangaUrlSelector)!!.absUrl("href"))
    }

    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".mc-genres-pills a"
    override val mangaDetailsSelectorDescription = ".mc-description-box"
    override val mangaDetailsSelectorAuthor = ".mc-meta-grid .attr-item:has(.attr-label:contains(AUTOR)) .attr-value"
    override val mangaDetailsSelectorArtist = ".mc-meta-grid .attr-item:has(.attr-label:contains(ARTISTA)) .attr-value"
    override val mangaDetailsSelectorStatus = ".mc-meta-grid .attr-item:has(.attr-label:contains(STATUS)) .attr-value"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        author = author?.replace(COMMA_REGEX, ", ")?.takeUnless { it.contains("---") }
        artist = artist?.replace(COMMA_REGEX, ", ")?.takeUnless { it.contains("---") }
    }

    // =============================== Chapters =================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()
        val mangaId = document.selectFirst("a.wp-manga-action-button")!!.attr("data-post")
        val chapters = mutableListOf<SChapter>()
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        var offset = 0
        do {
            val form = FormBody.Builder()
                .add("action", "load_more_chapters")
                .add("manga_id", mangaId)
                .add("offset", offset.toString())
                .build()
            offset += 12
            val dto = client.newCall(POST(url, headers, form)).execute().parseAs<ChapterDto>()
            val chapterElements = dto.toJsoup(baseUrl).select(chapterListSelector())
            chapters += chapterElements.map(::chapterFromElement)
        } while (!dto.isEmpty())

        chapters.sortedByDescending(SChapter::chapter_number)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("span.mc-chapter-title")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".mc-chapter-date")?.text())
        // The source chapter list is out of order, so extract the number here for later sorting
        CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.last()?.toFloatOrNull()?.let {
            chapter_number = it
        }
        setUrlWithoutDomain(element.selectFirst("a.mc-chapter-link")!!.absUrl("href"))
    }

    // =============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.asJsoup().html()
        val match = PAGES_REGEX.find(html) ?: return emptyList()

        val imageUrls = match.groupValues[1].split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
            .map { String(Base64.decode(it, Base64.DEFAULT)) }

        return imageUrls.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    // =============================== Images =================================

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Accept", "image/webp,image/*,*/*")
            .set("Referer", "$baseUrl/")
            .set("X-Reader-Sec", "tiraninha-web")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
        private val COMMA_REGEX = """,\s*""".toRegex()
        private val PAGES_REGEX = """var\s+pages\s*=\s*\[([\s\S]*?)\]""".toRegex()
    }
}
