package eu.kanade.tachiyomi.extension.pt.littletyrant

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LittleTyrant : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(ImageDecoderInterceptor())
        .rateLimit(3, 1.seconds)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Site", "same-origin")

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Popular =================================

    override fun popularMangaSelector() = "[id*=manga-entry-]"
    override val popularMangaUrlSelector = ".card-title a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst(popularMangaUrlSelector)!!.absUrl("href"))
    }

    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".genres-tax-list a"
    override val mangaDetailsSelectorDescription = ".summary-content-box"
    override val mangaDetailsSelectorAuthor = ".attr-item:has(.attr-label:contains(AUTOR)) .attr-value"
    override val mangaDetailsSelectorArtist = ".attr-item:has(.attr-label:contains(ARTISTA)) .attr-value"
    override val mangaDetailsSelectorStatus = ".attr-item:has(.attr-label:contains(STATUS)) .attr-value"

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
        name = element.selectFirst(".chapter-name-label")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".chapter-pub-date")?.text())
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // =============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(pages)")?.data()
            ?: return emptyList()

        val pages = PAGES_REGEX.find(script)?.groupValues?.last() ?: return emptyList()
        val tokenBaseUrl = BASE_URL_PAGE_REGEX.find(script)?.groupValues?.last()?.toHttpUrlOrNull() ?: return pages.parseAs<List<String>>()
            .mapIndexed { index, urlEncoded ->
                Page(index, imageUrl = Base64.decode(urlEncoded, Base64.DEFAULT).toString(Charsets.UTF_8))
            }

        val token = client
            .newCall(pageTokenRequest(tokenBaseUrl))
            .execute()
            .body.string()

        return pages
            .parseAs<List<String>>()
            .mapIndexed { index, pathSegment ->
                val decodePath = URLDecoder.decode(pathSegment, StandardCharsets.UTF_8.name())
                val imageUrl = "$baseUrl$decodePath".toHttpUrl().newBuilder()
                    .addQueryParameter("t_force", System.currentTimeMillis().toString())
                    .fragment(token)
                    .build().toString()
                Page(index, imageUrl = imageUrl)
            }
    }
    private fun pageTokenRequest(pageBaseUrl: HttpUrl): Request {
        val pageHeaders = headers.newBuilder()
            .set("X-Reader-Sec", "tiraninha-web")
            .build()
        return GET("$pageBaseUrl/gatekeeper.php?t=${System.currentTimeMillis()}", pageHeaders)
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
        private val PAGES_REGEX = """pages\s+=\s+(\[[^]]+])""".toRegex(RegexOption.IGNORE_CASE)
        private val BASE_URL_PAGE_REGEX = """_themePath\s+=\s+"([^"]+)""".toRegex(RegexOption.IGNORE_CASE)
    }
}
