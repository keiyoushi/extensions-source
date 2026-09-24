package eu.kanade.tachiyomi.extension.id.narasininja

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NarasiNinja : KeiSource() {

    // ======================== CSRF & Headers =================
    private var csrfToken: String? = null

    private suspend fun getCsrfToken(): String {
        csrfToken?.let { return it }

        val token = client.get("$baseUrl/komik").asJsoup()
            .selectFirst("meta[name=csrf-token]")
            ?.attr("content")
            ?: throw Exception("CSRF token tidak ditemukan")

        csrfToken = token
        return token
    }

    private suspend fun filterHeaders(): Headers = headersBuilder()
        .add("X-CSRF-TOKEN", getCsrfToken())
        .add("X-Requested-With", "XMLHttpRequest")
        .set("Referer", "$baseUrl/komik")
        .build()

    private suspend fun postFilter(page: Int, body: FormBody): FilterResponse {
        val url = "$baseUrl/komik/filter?page=$page"
        var response = client.post(url, filterHeaders(), body, ensureSuccess = false)
        if (response.code == 419) {
            response.close()
            csrfToken = null
            response = client.post(url, filterHeaders(), body, ensureSuccess = false)
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code)
        }
        return response.parseAs<FilterResponse>()
    }

    override val supportsLatest = false

    // ======================== Popular ========================
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderFilter()))

    // ======================== Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ======================== Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var status = ""
        var type = ""
        var order = ""
        val genres = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> status = filter.selectedValue()
                is TypeFilter -> type = filter.selectedValue()
                is OrderFilter -> order = filter.selectedValue()
                is GenreFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { genres.add(it.value) }
                else -> {}
            }
        }

        val body = FormBody.Builder()
            .add("search", query)
            .add("status", status)
            .add("type", type)
            .add("order", order)
            .apply {
                genres.forEach { add("genre[]", it) }
            }
            .build()

        val result = postFilter(page, body)
        val mangas = result.data.map { it.toSManga(baseUrl) }
        val hasNextPage = result.meta.currentPage < result.meta.lastPage

        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details & Chapters =============
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = mangaDetailsParse(document).apply { url = manga.url }
        val chapterList = chapterListParse(document)

        return SMangaUpdate(details, chapterList)
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")?.text()
            ?: throw Exception("Judul komik tidak ditemukan")
        thumbnail_url = document.selectFirst(".thumb img")?.attr("abs:src")
        description = document.selectFirst(".entry-content.entry-content-single p")?.text()
        status = document.selectFirst(".infotable tr:contains(Status) td:last-child")?.text().toStatus()
        genre = document.select(".seriestugenre a").joinToString { it.text() }
        author = document.selectFirst(".infotable tr:contains(Author) td:last-child")
            ?.text().takeUnless { it.isNullOrBlank() || it == "-" }
        artist = document.selectFirst(".infotable tr:contains(Artist) td:last-child")
            ?.text().takeUnless { it.isNullOrBlank() || it == "-" }
        initialized = true
    }

    private val chapterNumberRegex = Regex("""(\d+)(?:[._-](\d+))?""")

    private fun parseChapterNumber(raw: String): Float {
        val match = chapterNumberRegex.find(raw.trim()) ?: return -1f
        val major = match.groupValues[1].toIntOrNull() ?: return -1f
        val minor = match.groupValues[2].toIntOrNull()
        return if (minor != null) {
            "$major.$minor".toFloat()
        } else {
            major.toFloat()
        }
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        return document.select("#chapterlist li, .eplister li").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val nameText = li.selectFirst(".chapternum")?.text()?.ifEmpty { null }
                ?: link.text().ifEmpty { null }
                ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                name = nameText
                date_upload = li.selectFirst(".chapterdate")?.text()
                    ?.let { dateFormat.tryParseDate(it, zoneJakarta) } ?: 0L
                chapter_number = parseChapterNumber(
                    li.attr("data-num").ifEmpty {
                        name.substringAfterLast(" ")
                    },
                )
            }
        }.distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    // ======================== Pages ==========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#readerarea img.ts-main-image, #readerarea img").mapNotNull { img ->
            img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }.ifEmpty { null }
        }.distinct().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ======================== Deep Link =======================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (!url.host.removePrefix("www.").equals(baseHost, ignoreCase = true)) return null

        val komikIdx = url.pathSegments.indexOf("komik")
        if (komikIdx == -1 || url.pathSegments.size < komikIdx + 2) return null

        val slug = url.pathSegments[komikIdx + 1]
        if (slug.isEmpty()) return null

        val manga = SManga.create().apply { this.url = "/komik/$slug" }
        return runCatching {
            fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }.getOrNull()
    }

    // ======================== Helpers & Filters ==============
    private val zoneJakarta = ZoneId.of("Asia/Jakarta")
    private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        GenreFilter(),
    )
}
