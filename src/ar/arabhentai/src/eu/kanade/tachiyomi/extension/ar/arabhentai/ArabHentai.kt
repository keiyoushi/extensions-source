package eu.kanade.tachiyomi.extension.ar.arabhentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class ArabHentai : KeiSource() {

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = parseListing(client.get(listingUrl("11", page).toHttpUrl()).asJsoup())

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseListing(client.get(listingUrl("13", page).toHttpUrl()).asJsoup())

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return parseListing(client.get(url).asJsoup())
    }

    private fun listingUrl(sort: String, page: Int): String = "$baseUrl/search/manga".toHttpUrl().newBuilder()
        .addQueryParameter("status", "-1")
        .addQueryParameter("sort", sort)
        .apply { if (page > 1) addQueryParameter("page", page.toString()) }
        .build().toString()

    private fun parseListing(document: Document): MangasPage {
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

    // =============================== Details ==============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val document = client.get(url).asJsoup()
        return parseMangaDetails(document)
    }

    // ===================== Details + Chapters (update) ====================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.font-haffer, h1")!!.text()
        thumbnail_url = document.selectFirst("img[src*='mangaonl.com'], img[src*='covers/']")
            ?.attr("abs:src")
            ?: document.selectFirst("meta[property='og:image']")?.attr("content")
        status = document.selectFirst("p:contains(الحالة)")?.selectFirst("span[class]")?.text()
            .parseStatus()
        author = document.selectFirst("p:contains(المؤلف)")?.selectFirst("span[class]")?.text()
            ?.trim()?.ifBlank { null }
        genre = document.select("a[href*='genre=']").joinToString { it.text().trim() }
        description = document.selectFirst(
            "article[class*='bg-background-detail'], .description, .synopsis, .about",
        )?.text()
        initialized = true
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        contains("مكتمل", ignoreCase = true) || contains("النهاية", ignoreCase = true) -> SManga.COMPLETED
        contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    private fun parseChapterList(document: Document): List<SChapter> {
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
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter).toHttpUrl()).asJsoup()
        return document.select("img[alt^='Page'], img[src*='mangaonl.com']")
            .mapIndexedNotNull { index, item ->
                val imageUrl = item.attr("abs:src").ifBlank { item.attr("abs:data-original") }
                if (imageUrl.isBlank()) null else Page(index = index, imageUrl = imageUrl)
            }
    }

    // =============================== Filters ==============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
