package eu.kanade.tachiyomi.extension.id.lumoskomik

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.Locale

@Source
abstract class LumosKomik : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val dateFormatters by lazy {
        listOf(
            java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US),
            java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US),
        )
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest")
            .addQueryParameter("page", page.toString())
            .build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("sort", filter.selected)
                    }
                }
                is StatusFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selected)
                    }
                }
                is TypeFilter -> {
                    if (filter.selected.isNotEmpty()) {
                        url.addQueryParameter("type", filter.selected)
                    }
                }
                is GenreFilter -> {
                    val genre = filter.selected
                    if (genre.isNotEmpty()) {
                        url.addQueryParameter("genre", genre)
                    }
                }
                is MinChapterFilter -> {
                    val min = filter.state
                    if (min.isNotEmpty()) {
                        url.addQueryParameter("minChapters", min)
                    }
                }
                else -> {}
            }
        }

        return mangaListParse(client.get(url.build()).asJsoup())
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================== List ==================================

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select(".htg-card, div.group.flex.flex-col.htg-card").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[href*=page=][aria-label=Selanjutnya], a[aria-label=Selanjutnya]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkEl: Element = element.selectFirst("a.htg-card-cover") ?: element.selectFirst("a[href*=/comic/]")!!
        setUrlWithoutDomain(linkEl.absUrl("href"))
        val titleEl: Element = element.selectFirst("h3")!!
        title = titleEl.text()
        val imgEl: Element? = element.selectFirst("img")
        thumbnail_url = imgEl?.absUrl("src")
    }

    // ============================== Details ===============================

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        val path = normalizeMangaUrl(manga.url)
        setUrlWithoutDomain(path)
        val h1El: Element? = document.selectFirst("h1")
        title = h1El?.text() ?: manga.title

        val dataSrEl: Element? = document.selectFirst("#synopsis-wrapper [data-sr]")
        val dataSr: String? = dataSrEl?.attr("data-sr")
        description = if (!dataSr.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.decode(dataSr, Base64.DEFAULT)
                if (decodedBytes != null) {
                    String(decodedBytes, Charsets.UTF_8)
                } else {
                    val fallbackEl: Element? = document.selectFirst("#synopsis-wrapper [data-sr]")
                    fallbackEl?.text()
                }
            } catch (_: Exception) {
                val fallbackEl: Element? = document.selectFirst("#synopsis-wrapper [data-sr]")
                fallbackEl?.text()
            }
        } else {
            val fallbackEl: Element? = document.selectFirst("#synopsis-wrapper")
            fallbackEl?.text()
        }

        genre = document.select("a[href*=genre=]").joinToString { it.text() }.ifEmpty { null }

        val authorEl: Element? = document.selectFirst("div:has(span:contains(Author)) > span:last-child")
        author = authorEl?.text()
        val artistEl: Element? = document.selectFirst("div:has(span:contains(Artist)) > span:last-child")
        artist = artistEl?.text()

        val statusEl: Element? = document.selectFirst("div:has(div:contains(Status)) > div:last-child")
        val statusStr = statusEl?.text()?.lowercase()
        status = when (statusStr) {
            "ongoing" -> SManga.ONGOING
            "completed", "tamat" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val metaEl: Element? = document.selectFirst("meta[property=og:image]")
        val ogImage = metaEl?.attr("content")
        thumbnail_url = if (!ogImage.isNullOrEmpty()) {
            ogImage
        } else {
            val imgEl: Element? = document.selectFirst("img[src*=/cover_], img[fetchpriority=high]")
            imgEl?.absUrl("src")
        }
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document, manga: SManga): List<SChapter> {
        val mangaPath = normalizeMangaUrl(manga.url)
        val slug = mangaPath.substringAfterLast("/")

        return document.select("a[href*=/read/]").map { element ->
            val href = element.absUrl("href")
            val chapterSlug = href.removeSuffix("/").substringAfterLast("/")
            SChapter.create().apply {
                url = "/read/$slug/$chapterSlug"
                val nameEl: Element = element.selectFirst("span.font-semibold") ?: element.selectFirst("span")!!
                name = nameEl.text()

                val dateEl: Element? = element.selectFirst("span.tabular-nums")
                val dateStr = dateEl?.text()
                date_upload = parseDate(dateStr)

                val numberStr = chapterSlug.substringAfter("chapter-")
                chapter_number = numberStr.toFloatOrNull() ?: -1f
            }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.encodedPath.removeSuffix("/")
        val slug = path.substringAfterLast("/")
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/comic/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val normalizedMangaUrl = normalizeMangaUrl(manga.url)
        val document = client.get("$baseUrl$normalizedMangaUrl").asJsoup()
        return SMangaUpdate(
            manga = if (fetchDetails) mangaDetailsParse(document, manga) else manga,
            chapters = if (fetchChapters) chapterListParse(document, manga) else chapters,
        )
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val normalizedChapterUrl = normalizeChapterUrl(chapter.url)
        val document = client.get("$baseUrl$normalizedChapterUrl").asJsoup()

        val images = document.select("img[src*=/comic/], img[src*=/read/], img[alt^=Page]")
            .filter { img ->
                val src = img.attr("src")
                src.endsWith(".webp", ignoreCase = true) ||
                    src.endsWith(".jpg", ignoreCase = true) ||
                    src.endsWith(".jpeg", ignoreCase = true) ||
                    src.endsWith(".png", ignoreCase = true) ||
                    src.contains("/chapter-")
            }
            .distinctBy { it.absUrl("src") }

        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    // ============================== Utilities =============================

    private fun normalizeMangaUrl(url: String?): String {
        val path = url.orEmpty()
        val httpUrl = path.toHttpUrlOrNull()
            ?: if (path.startsWith("/")) "$baseUrl$path".toHttpUrlOrNull() else "$baseUrl/$path".toHttpUrlOrNull()
        val slug = if (httpUrl != null) {
            httpUrl.pathSegments.lastOrNull { it.isNotEmpty() } ?: ""
        } else {
            ""
        }
        return "/comic/$slug"
    }

    private fun normalizeChapterUrl(url: String?): String {
        val path = url.orEmpty()
        val httpUrl = path.toHttpUrlOrNull()
            ?: if (path.startsWith("/")) "$baseUrl$path".toHttpUrlOrNull() else "$baseUrl/$path".toHttpUrlOrNull()
        val segments = if (httpUrl != null) {
            httpUrl.pathSegments.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        if (segments.size >= 3 && segments[0] == "komik") {
            val slug = segments[1]
            val chapterSlug = segments[2]
            return "/read/$slug/$chapterSlug"
        }
        val slug = segments.getOrNull(segments.size - 2) ?: ""
        val chapterSlug = segments.lastOrNull() ?: ""
        return "/read/$slug/$chapterSlug"
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + normalizeMangaUrl(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + normalizeChapterUrl(chapter.url)

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val dateLower = dateStr.lowercase()
        if (dateLower.contains("lalu") || dateLower.contains("baru saja") || dateLower.contains("sekarang")) {
            return parseRelativeDate(dateLower)
        }

        val months = listOf("januari", "februari", "maret", "april", "mei", "juni", "juli", "agustus", "september", "oktober", "november", "desember")
        val shortMonths = listOf("jan", "feb", "mar", "apr", "mei", "jun", "jul", "agu", "sep", "okt", "nov", "des")

        val regex = Regex("""(\d+)\s+([a-zA-Z]+)\s+(\d+)""")
        val match = regex.find(dateLower)
        if (match != null) {
            val day = match.groupValues[1].toIntOrNull() ?: 1
            val monthStr = match.groupValues[2]
            val year = match.groupValues[3].toIntOrNull() ?: 2025

            var monthIdx = months.indexOf(monthStr)
            if (monthIdx == -1) monthIdx = shortMonths.indexOf(monthStr)
            if (monthIdx != -1) {
                val calendar = Calendar.getInstance()
                calendar.set(year, monthIdx, day, 0, 0, 0)
                return calendar.timeInMillis
            }
        }

        for (formatter in dateFormatters) {
            val parsedTime = formatter.tryParse(dateStr)
            if (parsedTime != 0L) return parsedTime
        }

        return 0L
    }

    private fun parseRelativeDate(dateStr: String): Long {
        if (dateStr.contains("baru saja") || dateStr.contains("sekarang")) {
            return System.currentTimeMillis()
        }

        val parts = dateStr.split(" ")
        val value = parts.getOrNull(0)?.toIntOrNull() ?: return 0L
        val unit = parts.getOrNull(1) ?: return 0L

        val calendar = Calendar.getInstance()
        when {
            unit.contains("detik") -> calendar.add(Calendar.SECOND, -value)
            unit.contains("menit") -> calendar.add(Calendar.MINUTE, -value)
            unit.contains("jam") -> calendar.add(Calendar.HOUR_OF_DAY, -value)
            unit.contains("hari") -> calendar.add(Calendar.DATE, -value)
            unit.contains("minggu") -> calendar.add(Calendar.DATE, -value * 7)
            unit.contains("bulan") -> calendar.add(Calendar.MONTH, -value)
            unit.contains("tahun") -> calendar.add(Calendar.YEAR, -value)
        }
        return calendar.timeInMillis
    }
}
