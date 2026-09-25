package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LittleTyrant : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("pt-BR"))

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(ImageDecoderInterceptor())
        rateLimit(3, 1.seconds)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Sec-Fetch-Mode", "cors")
        set("Sec-Fetch-Dest", "empty")
        set("Sec-Fetch-Site", "same-origin")
    }

    // =============================== Popular =================================

    override fun archiveSelector() = "[id*=manga-entry-]"
    override val archiveUrlSelector = ".card-title a"
    override val archiveTitleSelector = "h3"
    override fun Element.postId() = id().substringAfter("manga-entry-")
    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".genres-tax-list a"
    override val mangaDetailsSelectorDescription = ".summary-content-box"
    override val mangaDetailsSelectorAuthor = ".attr-item:has(.attr-label:contains(AUTOR)) .attr-value"
    override val mangaDetailsSelectorArtist = ".attr-item:has(.attr-label:contains(ARTISTA)) .attr-value"
    override val mangaDetailsSelectorStatus = ".attr-item:has(.attr-label:contains(STATUS)) .attr-value"

    // =============================== Chapters =================================

    override val chapterNameSelector = ".chapter-name-label"
    override val chapterDateSelector = ".chapter-pub-date"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        var offset = 0
        do {
            val form = FormBody.Builder()
                .add("action", "load_more_chapters")
                .add("manga_id", id)
                .add("offset", offset.toString())
                .build()
            offset += 12
            val dto = client.post(url, form).parseAs<ChapterDto>()
            val chapterElements = dto.toJsoup(baseUrl).select(chapterListSelector())
            chapters += chapterElements.mapNotNull { chapterFromElement(it, mangaPath) }
        } while (!dto.isEmpty())

        return chapters.sortedByDescending(SChapter::chapter_number)
    }

    // =============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val script = document.selectFirst("script:containsData(_proxyUrls)")?.data()
            ?: return emptyList()

        val pages = PROXY_URLS_REGEX.find(script)?.groupValues?.last() ?: return emptyList()
        val themePath = BASE_URL_PAGE_REGEX.find(script)?.groupValues?.last()
            ?.replace("\\/", "/")
            ?.toHttpUrlOrNull() ?: return emptyList()

        val token = getToken(themePath)

        val baseUrl = "${themePath.scheme}://${themePath.host}"

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

    private suspend fun getToken(pageBaseUrl: HttpUrl): String {
        val pageHeaders = headersBuilder()
            .set("X-Reader-Sec", "tiraninha-web")
            .build()
        val url = "$pageBaseUrl/gatekeeper.php?t=${System.currentTimeMillis()}"
        return client.get(url, pageHeaders).body.string()
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
        private val PROXY_URLS_REGEX = """_proxyUrls\s*=\s*(\[[^]]+])""".toRegex(RegexOption.IGNORE_CASE)
        private val BASE_URL_PAGE_REGEX = """_themePath\s+=\s+"([^"]+)""".toRegex(RegexOption.IGNORE_CASE)
    }
}
