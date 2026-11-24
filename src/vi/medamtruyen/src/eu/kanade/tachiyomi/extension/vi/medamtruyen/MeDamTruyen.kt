package eu.kanade.tachiyomi.extension.vi.medamtruyen

import eu.kanade.tachiyomi.multisrc.koi.Koi
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MeDamTruyen : Koi() {

    override val mangaPath = "truyen"

    override val filterTabs = listOf(
        "Thể loại" to "#genre-tab",
        "Nhóm" to "#team-tab",
        "Tác giả" to "#artist-tab",
    )

    override val chapterMangaLinkSelector = "a[href*=/truyen/]:matchesOwn(^DS\\. Chương$), a[href*=/truyen/]"

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#day-charts .sidebar-comic-block")
            .mapNotNull(::mangaFromBlock)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getFilterMangaList("/truyen-moi-cap-nhat/", page)

    // ============================== Search ================================

    override suspend fun getFilterMangaList(path: String, page: Int): MangasPage {
        val url = (baseUrl + path).toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.comic-list-item")
            .mapNotNull(::mangaFromBlock)
            .distinctBy { it.url }
        val hasNextPage = document.select("a[href*='?trang=']").any { element ->
            element.text().contains("Sau", ignoreCase = true)
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromBlock(element: Element): SManga? {
        val linkElement = element.selectFirst("a.sidebar-comic-block-link[href*=/truyen/], a.comic-block-link[href*=/truyen/]") ?: return null
        val titleElement = element.selectFirst("h3.sidebar-comic-block-title, h3.comic-block-title") ?: return null

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img.koi-img, div.comic-block-img img")?.coverUrl()
        }
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        val isChapterUrl = pathSegments.size == 1 && "-chap-" in pathSegments.single().lowercase()
        if (pathSegments.firstOrNull() != mangaPath && !isChapterUrl) return null

        return super.getMangaByUrl(url)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoRows = document.select("div.comic-desc-list ul.list-unstyled li")

        title = document.selectFirst("h2.comic-title")!!.text()
        thumbnail_url = document.selectFirst("div.comic-desc-list meta[itemprop=image]")
            ?.absUrl("content")
            ?.takeIf { it.isNotEmpty() }
            ?.toCoverUrl()
            ?: document.selectFirst("div.comic-info-img img, div.chapter-item-img img")?.coverUrl()
        author = extractInfoValue(infoRows, "Tác giả")?.takeUnless(::isPlaceholder)
        genre = document.select("div.comic-desc-tags a[href*=/the-loai/]")
            .joinToString { it.text().removePrefix("#") }
            .ifEmpty { null }
        status = parseStatus(document.selectFirst("span.comic-stt")?.text())
        description = parseDescription(document.selectFirst(".title-block:has(h3:containsOwn(Giới Thiệu)) + div"))
    }

    private fun extractInfoValue(infoRows: List<Element>, label: String): String? = infoRows.firstOrNull { row ->
        row.selectFirst("strong")?.text()?.contains(label, ignoreCase = true) == true
    }?.ownText()?.takeIf { it.isNotEmpty() }

    // ============================== Chapters ==============================

    override suspend fun chapterListParse(document: Document): List<SChapter> = document.select("div.chapter-list div.chapter-item")
        .mapNotNull(::chapterFromElement)
        .distinctBy { it.url }

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.chapter-link[href*=-chap-]") ?: return null
        val rawChapterName = element.selectFirst("p.chapter-title")?.text() ?: linkElement.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(linkElement.absUrl("href"))
            val normalizedName = normalizeChapterName(rawChapterName)
            name = if (element.isLocked()) "🔒 $normalizedName" else normalizedName
            date_upload = element.selectFirst("p.chapter-meta")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L

            chapterNumberRegex.find(name)?.value?.replace(",", ".")?.toFloatOrNull()?.let {
                chapter_number = it
            }
        }
    }

    private fun normalizeChapterName(rawChapterName: String): String {
        val chapterMatch = chapterNameRegex.find(rawChapterName) ?: return rawChapterName
        return chapterMatch.value
            .replace(chapterWordRegex, "Chap")
            .replace(multiSpaceRegex, " ")
    }

    private fun parseChapterDate(chapterMeta: String): Long = chapterDateFormat.tryParseDate(chapterDateRegex.find(chapterMeta)?.value, dateZone)

    // ============================== Pages =================================

    override fun getImageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // =============================== Related ==============================

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val relatedList = document.select("h3")
            .firstOrNull { it.text().equals("Truyện Liên Quan", ignoreCase = true) }
            ?.parent()
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedList.select("div.comic-block")
            .mapNotNull(::mangaFromBlock)
            .distinctBy { it.url }
    }

    private val chapterNameRegex = Regex(
        """chap\s*\d+(?:[.,]\d+)?(?:\s*:\s*.+)?""",
        RegexOption.IGNORE_CASE,
    )
    private val chapterWordRegex = Regex("""chap""", RegexOption.IGNORE_CASE)
    private val chapterNumberRegex = Regex("""\d+(?:[.,]\d+)?""")
    private val chapterDateRegex = Regex("""\d{2}/\d{2}/\d{2}""")
    private val multiSpaceRegex = Regex("""\s+""")
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
}
