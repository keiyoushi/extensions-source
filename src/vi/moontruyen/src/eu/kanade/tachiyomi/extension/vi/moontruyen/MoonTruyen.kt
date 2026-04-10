package eu.kanade.tachiyomi.extension.vi.moontruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MoonTruyen : HttpSource() {
    override val name = "MoonTruyen"
    override val lang = "vi"
    override val baseUrl = "https://moontruyen.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach/yeu-thich/page-$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/moi-cap-nhat/page-$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem/page-$page/".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()

            return GET(url, headers)
        }

        val genreUriPart = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        if (genreUriPart != null) {
            return GET("$baseUrl/the-loai/$genreUriPart/page-$page/", headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    override fun getFilterList(): FilterList = getFilters()

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".m_l_col > .mcol_ct > .mcol_pos")
            .map(::mangaFromElement)

        val hasNextPage = document.selectFirst("a.paging_prevnext.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val mangaLink = element.selectFirst(".content_normal a[href*=/truyen/], a[href*=/truyen/]")!!
        setUrlWithoutDomain(mangaLink.absUrl("href"))
        title = element.selectFirst(".ct_title")!!.text()
        thumbnail_url = element.selectFirst(".img_link")?.absUrl("data-bg")
            ?.ifEmpty { element.selectFirst(".img_load img")?.absUrl("src") }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".de_title.comictitle")!!.text()
            author = infoValue(document, "Tác giả")
            status = parseStatus(infoValue(document, "Trạng thái"))
            genre = document.select(".lt_cate a")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = parseDescription(document)
            thumbnail_url = document.selectFirst(".comicthumb .img_link")?.absUrl("data-bg")
                ?.ifEmpty { document.selectFirst(".comicthumb img")?.absUrl("src") }
        }
    }

    private fun infoValue(document: Document, label: String): String? = document
        .select(".lt_infocomic .cti_comic")
        .firstOrNull { element ->
            element.selectFirst(".lsub")
                ?.text()
                ?.contains(label, ignoreCase = true)
                ?: false
        }
        ?.selectFirst(".rsub")
        ?.text()
        ?.ifEmpty { null }

    private fun parseDescription(document: Document): String? {
        val paragraph = document.selectFirst(".lt_info99 p")?.clone() ?: return null
        val expandedContent = paragraph.selectFirst("#themnoidung")?.text()
        paragraph.select("#themnoidung, #chamcham, #xemthem").remove()

        return listOfNotNull(paragraph.text(), expandedContent)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifEmpty { null }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        statusText.contains("Tạm ngưng", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".list-chapter .table-content .table-row")
        .mapNotNull(::chapterFromElement)

    private fun chapterFromElement(element: Element): SChapter? {
        val chapterLink = element.selectFirst(".table-data.chapter a") ?: return null

        return SChapter.create().apply {
            setUrlWithoutDomain(chapterLink.absUrl("href"))
            name = chapterLink.text()

            val dateElement = element.select(".table-data").getOrNull(1)
            val chapterDate = dateElement?.attr("title")?.ifEmpty { dateElement.text() }
            date_upload = parseChapterDate(chapterDate)
        }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        return DATE_FORMAT.tryParse(dateText)
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val keyText = extractImageDecryptKey(document)
        val keyBytes = keyText.toByteArray()

        val imageUrls = document.select(".chapter-content img")
            .mapNotNull { element ->
                element.attr("data-encdes")
                    .takeIf(String::isNotBlank)
                    ?.let { CryptoAES.decrypt(it, keyBytes, keyBytes) }
                    ?.takeIf(String::isNotBlank)
                    ?.let(::normalizeImageUrl)
                    ?: sequenceOf(
                        element.absUrl("data-src"),
                        element.absUrl("data-original"),
                        element.absUrl("src"),
                    ).firstOrNull { url ->
                        url.isNotBlank() &&
                            !url.startsWith("data:", ignoreCase = true) &&
                            !url.contains(LOADING_IMAGE_PATH)
                    }?.let(::normalizeImageUrl)
            }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = imageUrl)
        }
    }

    private fun extractImageDecryptKey(document: Document): String {
        val scriptData = document.select("script")
            .asSequence()
            .map(Element::data)

        val keyBase = scriptData
            .mapNotNull { KEY_BASE_REGEX.find(it)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?: return DEFAULT_IMAGE_DECRYPT_KEY

        val keySuffix = document.select("script")
            .asSequence()
            .map(Element::data)
            .mapNotNull { KEY_SUFFIX_REGEX.find(it)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        return if (keySuffix != null) keyBase + keySuffix else keyBase
    }

    private fun normalizeImageUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> url
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val DEFAULT_IMAGE_DECRYPT_KEY = "%DBjZh[tcNdK4msQ"
        private const val LOADING_IMAGE_PATH = "/images/hinh-loading.png"
        private val KEY_BASE_REGEX = Regex("""var\s+encdesbase\s*=\s*['"]([^'"]+)['"]""")
        private val KEY_SUFFIX_REGEX = Regex("""encdesbase\s*\+=\s*['"]([^'"]+)['"]""")

        private val DATE_FORMAT = SimpleDateFormat("dd/MM/yy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
