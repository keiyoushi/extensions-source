package eu.kanade.tachiyomi.extension.zh.kaixinman

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Kaixinman : HttpSource() {
    override val name = "Kaixinman"
    override val baseUrl = "https://www.kaixinman.com"
    override val lang = "zh"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", DEFAULT_USER_AGENT)
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

    private fun pcHeaders(referer: String = baseUrl): Headers = headersBuilder()
        .add("Referer", referer)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(
        if (page == 1) "$baseUrl/ranking" else "$baseUrl/ranking?page=$page",
        pcHeaders("$baseUrl/ranking"),
    )

    override fun popularMangaParse(response: Response): MangasPage = parseMangaGrid(response.asJsoup())

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(
        if (page == 1) "$baseUrl/update" else "$baseUrl/update?page=$page",
        pcHeaders("$baseUrl/update"),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaGrid(response.asJsoup())

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = buildString {
            append(baseUrl).append("/search?keyword=")
            append(URLEncoder.encode(query, "UTF-8"))
            if (page > 1) append("&page=").append(page)
        }
        return GET(url, pcHeaders("$baseUrl/search"))
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaGrid(response.asJsoup())

    private fun parseMangaGrid(document: Document): MangasPage {
        val mangas = document.select("div.comic-item").mapNotNull(::mangaFromElement)
        val hasNextPage = document.select(".pagination a, ul.pagination a").any {
            val text = it.text().trim()
            text.contains("下一") || text == ">" || it.className().contains("next", ignoreCase = true)
        }
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("div.comic-cover a.thumb-link, h4.title a, .comic-info a[href^=/comic/]") ?: return null
        return SManga.create().apply {
            title = element.selectFirst("h4.title a")?.text()?.ifBlank { null }
                ?: link.attr("title").ifBlank { null }
                ?: return null
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = element.selectFirst("div.comic-cover")?.attr("data-original")?.ifBlank { null }
                ?: element.selectFirst("img")?.absUrl("src")?.ifBlank { null }
            author = element.selectFirst(".text-actor")?.text()?.ifBlank { null }
            description = element.selectFirst("p.text")?.text()?.ifBlank { null }
        }
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, pcHeaders(baseUrl + manga.url))

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst("div.comic-detail div.col-sm-10, div.detail-main, div.info") ?: document
        return SManga.create().apply {
            title = info.selectFirst("h1.comic-title, h1")?.ownText()?.ifBlank { null }
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: ""
            thumbnail_url = document.selectFirst("div.comic-cover img, div.cover img, meta[property=og:image]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.absUrl("src")
            }
            author = info.select("p.data span.text-muted").firstOrNull { it.text().contains("作者：") }
                ?.text()?.substringAfter("作者：")?.trim()
            genre = info.select("p.data span.text-muted a").eachText().joinToString(", ").ifBlank { null }
            description = info.selectFirst("p.desc span, .desc, meta[name=description]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            }?.substringAfter("简介：")?.trim()
            status = SManga.UNKNOWN
            artist = author
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("ul.chapter-list li a")
        .map {
            SChapter.create().apply {
                name = it.text().trim()
                setUrlWithoutDomain(it.absUrl("href"))
            }
        }
        .reversed()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, pcHeaders(baseUrl + chapter.url))

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val encodedData = PARAMS_REGEX.find(body)?.groupValues?.get(1)
            ?: error("Cannot find encrypted chapter params")
        val chapterData = decodeChapterData(encodedData)
        if (chapterData.host != response.request.url.host) {
            error("Decoded host mismatch: ${chapterData.host}")
        }
        val prefix = chapterData.imagesDomain.ifBlank { chapterData.chapterDomain }.ifBlank { baseUrl }
        return chapterData.chapterImages.mapIndexed { index, path ->
            val imageUrl = when {
                path.startsWith("http://") || path.startsWith("https://") -> path
                path.startsWith("//") -> "https:$path"
                else -> prefix.trimEnd('/') + "/" + path.trimStart('/')
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headersBuilder()
            .add("Referer", baseUrl)
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build(),
    )

    private fun decodeChapterData(encodedData: String): ChapterData {
        val decodedBase64 = Base64.decode(encodedData, Base64.DEFAULT)
        val iv = IvParameterSpec(decodedBase64.copyOfRange(0, 16))
        val cipherText = decodedBase64.copyOfRange(16, decodedBase64.size)
        val secretKeySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv)
        val decryptedText = cipher.doFinal(cipherText).toString(Charsets.UTF_8)
        return decryptedText.parseAs<ChapterData>()
    }

    @Serializable
    data class ChapterData(
        val host: String,
        @SerialName("chapter_images") val chapterImages: List<String>,
        @SerialName("chapter_domain") val chapterDomain: String = "",
        @SerialName("images_domain") val imagesDomain: String = "",
    )

    companion object {
        private val PARAMS_REGEX = Regex("""params\\s*=\\s*'([A-Za-z0-9+/=]+)'""")
        private const val AES_KEY = "5V&RoR%Jf@pJPydF"
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    }
}
