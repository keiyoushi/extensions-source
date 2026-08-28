package eu.kanade.tachiyomi.extension.ar.hentailek

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class HentaiLek : KeiSource() {
    override val supportsLatest = true

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(listingUrl("popular", page).toHttpUrl()).asJsoup()
        return parseListing(document)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(listingUrl("latest", page).toHttpUrl()).asJsoup()
        return parseListing(document)
    }

    // =============================== Search ===============================
    // The site's search does not paginate ("page" returns 0 results beyond 1).
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()
        return parseListing(client.get(url).asJsoup())
    }

    private fun listingUrl(mode: String, page: Int): String = "$baseUrl/library".toHttpUrl().newBuilder()
        .apply {
            if (mode == "popular") addQueryParameter("sort", "popular")
            if (page > 1) addQueryParameter("page", page.toString())
        }
        .build().toString()

    private fun parseListing(document: Document): MangasPage {
        val mangas = document.select("a[href*='/manga/']:not([href*='/chapter-'])")
            .mapNotNull { it.toManga() }
        val current = document.location().substringAfter("page=", "").substringBefore("&").toIntOrNull() ?: 1
        val hasNextPage = document.select("a[href*='page=']")
            .any { link ->
                val page = link.attr("href").substringAfter("page=", "").substringBefore("&").toIntOrNull()
                page != null && page == current + 1
            }
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toManga(): SManga? {
        val link = absUrl("href")
        if (link.isBlank() || !link.contains("/manga/") || link.contains("/chapter-")) return null
        val img = selectFirst("img[src]")
        val title = selectFirst("h3")?.text()
            ?: attr("aria-label").ifBlank { img?.attr("alt") }
            ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link)
            this.title = title.trim()
            thumbnail_url = img?.attr("abs:src")
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
        val document = client.get(getMangaUrl(manga).toHttpUrl()).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        thumbnail_url = document.selectFirst("img[src*='cover']")?.attr("abs:src")
            ?: document.selectFirst("meta[property='og:image']")?.attr("content")
        status = infoValue(document, "الحالة").parseStatus()
        author = infoValue(document, "المؤلف")
        artist = infoValue(document, "الرسم")
        genre = infoValue(document, "التصنيفات")
        description = document.select("p.border-t.text-sm.leading-relaxed")
            .maxByOrNull { it.text().length }
            ?.text()
        initialized = true
    }

    private fun infoValue(document: Document, key: String): String? = document.selectFirst("dt:contains($key) + dd")?.text()?.trim()?.ifBlank { null }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
        contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    private fun parseChapterList(document: Document): List<SChapter> = document.select("a[href*='/chapter-'][class*='group']").mapNotNull { it.toChapter() }

    private fun Element.toChapter(): SChapter? {
        val url = absUrl("href")
        if (url.isBlank()) return null
        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            name = select("span:containsOwn(الفصل)").firstOrNull()
                ?.ownText()
                ?.trim()
                ?.ifBlank { null }
                ?: url.substringAfterLast("/")
            date_upload = selectFirst("span.text-xs.text-muted")?.text().parseArabicDate()
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter).toHttpUrl()).asJsoup()
        return document.select(".reader-page img[src], .reader-page img[data-src], .on-canvas img")
            .mapIndexedNotNull { index, item ->
                val imageUrl = item.attr("abs:src").ifBlank { item.attr("abs:data-src") }
                if (imageUrl.isBlank()) null else Page(index = index, imageUrl = imageUrl)
            }
    }

    // =============================== Filters ==============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    // ========================= Date helpers ===============================
    private val arabicMonths = mapOf(
        "يناير" to 1, "فبراير" to 2, "مارس" to 3, "أبريل" to 4,
        "مايو" to 5, "يونيو" to 6, "يوليو" to 7, "أغسطس" to 8,
        "سبتمبر" to 9, "أكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12,
    )

    private fun String?.parseArabicDate(): Long {
        if (this.isNullOrBlank()) return 0L
        val parts = split(Regex("\\s+"))
        if (parts.size < 3) return 0L
        val day = parts[0].toIntOrNull() ?: return 0L
        val month = arabicMonths[parts[1].trim()] ?: return 0L
        val year = parts[2].toIntOrNull() ?: return 0L
        return try {
            Calendar.getInstance().apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }
}
