package eu.kanade.tachiyomi.extension.ar.arabhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class ArabHentai : HttpSource() {
    override val supportsLatest = true

    // The site sorts by popularity for a time period via the "sort" parameter.
    private val popularSort = "11"
    private val latestSort = "13"

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(listingUrl(popularSort, page), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseListing(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET(listingUrl(latestSort, page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseListing(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query.trim())
            .apply {
                if (page > 1) addQueryParameter("page", page.toString())
                val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
                genreFilter?.state?.filter { it.state }?.forEach { addQueryParameter("genre", it.uriPart) }
                val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
                statusFilter?.state?.filter { it.state }?.forEach { addQueryParameter("status", it.uriPart) }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseListing(response)

    private fun listingUrl(sort: String, page: Int): String = "$baseUrl/search/manga".toHttpUrl().newBuilder()
        .addQueryParameter("status", "-1")
        .addQueryParameter("sort", sort)
        .apply { if (page > 1) addQueryParameter("page", page.toString()) }
        .build().toString()

    private fun parseListing(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href*='/manga/']:not([href*='/chapter-'])")
            .mapNotNull { it.toManga() }
        // A "›" (next) pagination arrow link exists when there is a following page.
        val hasNextPage = document.select("a[href*='page=']").any { it.text() == "›" }
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toManga(): SManga? {
        val link = absUrl("href")
        if (link.isBlank()) return null
        val title = selectFirst(".font-haffer, img[alt]")
            ?.let { it.ownText().ifBlank { it.attr("alt") } }
            ?.trim()
        if (title.isNullOrBlank()) return null
        return SManga.create().apply {
            setUrlWithoutDomain(link)
            this.title = title
            thumbnail_url = selectFirst("img[data-original]")?.attr("abs:data-original")
                ?: selectFirst("img[src]")?.attr("abs:src")
        }
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.font-haffer, h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("img[src*='mangaonl.com'], img[src*='covers/']")
                ?.attr("abs:src")
                ?: document.selectFirst("meta[property='og:image']")?.attr("content")
            status = document.selectFirst("p:contains(الحالة)")?.selectFirst("span[class]")?.text()
                .parseStatus()
            author = document.selectFirst("p:contains(المؤلف)")?.selectFirst("span[class]")?.text()
                ?.trim()?.ifBlank { null }
            genre = document.select("a[href*='genre=']").joinToString { it.text().trim() }
            description = document.selectFirst("article[class*='bg-background-detail'], .description, .synopsis, .about")
                ?.text()
            initialized = true
        }
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        contains("مكتمل", ignoreCase = true) || contains("النهاية", ignoreCase = true) -> SManga.COMPLETED
        contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // Exclude the "قراءة من البداية" and "قراءة الأحدث" buttons, which also link
        // to /chapter- pages but carry a rounded-2xl class, unlike real chapter entries.
        return document.select("a[href*='/chapter-']:not([class*='rounded-2xl'])").mapNotNull { it.toChapter() }
    }

    private fun Element.toChapter(): SChapter? {
        val url = absUrl("href")
        if (url.isBlank()) return null
        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            name = selectFirst(".font-semibold")?.text()
                ?: selectFirst("p")?.text()
                ?: url.substringAfterLast("/")
            date_upload = selectFirst(".text-lightgray")?.text().parseRelativeDate()
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[alt^='Page'], img[src*='mangaonl.com']")
            .mapIndexedNotNull { index, item ->
                val imageUrl = item.attr("abs:src").ifBlank { item.attr("abs:data-original") }
                if (imageUrl.isBlank()) null else Page(index = index, imageUrl = imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ==============================
    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter(),
    )

    // ========================= Date helper ================================
    private fun String?.parseRelativeDate(): Long {
        if (this.isNullOrBlank()) return 0L
        val (amount, unit) = Regex("""(\d+)\s*([hidwmsy])""").find(
            this.replace("منذ", "")
                .replace("ساعة", "1h").replace("ساعه", "1h")
                .replace("أيام", "d").replace("يوم", "d")
                .replace("أسبوع", "7d").replace("اسبوع", "7d")
                .replace("شهر", "mo").replace("سنة", "y").replace("عام", "y"),
        )?.destructured ?: return 0L
        val value = amount.toLong()
        val multiplier = when (unit) {
            "h", "i" -> 3_600_000L
            "d", "w" -> 24 * 3_600_000L
            "mo" -> 30L * 24 * 3_600_000L
            else -> 365L * 24 * 3_600_000L
        }
        return System.currentTimeMillis() - value * multiplier
    }
}
