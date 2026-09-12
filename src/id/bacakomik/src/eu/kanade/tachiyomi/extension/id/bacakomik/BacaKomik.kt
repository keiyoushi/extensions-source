package eu.kanade.tachiyomi.extension.id.bacakomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BacaKomik : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val chapterRegex = Regex("""Chapter\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(12, 3.seconds)

    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/daftar-komik/${pagePath(page)}?order=popular".toHttpUrl()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/daftar-komik/${pagePath(page)}?order=update".toHttpUrl()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="
        val url = builtUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("title", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter("author", filter.state)
                        }
                    }
                    is YearFilter -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter("yearx", filter.state)
                        }
                    }
                    is StatusFilter -> {
                        val status = filter.toUriPart()
                        if (status.isNotEmpty()) {
                            addQueryParameter("status", status)
                        }
                    }
                    is TypeFilter -> {
                        val type = filter.toUriPart()
                        if (type.isNotEmpty()) {
                            addQueryParameter("type", type)
                        }
                    }
                    is SortByFilter -> {
                        val order = filter.toUriPart()
                        if (order.isNotEmpty()) {
                            addQueryParameter("order", order)
                        }
                    }
                    is GenreListFilter -> {
                        filter.state
                            .filter { it.state != Filter.TriState.STATE_IGNORE }
                            .forEach { addQueryParameter("genre[]", it.id) }
                    }
                    else -> {}
                }
            }
        }.build()

        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val targetUrl = "$baseUrl/komik/$slug/".toHttpUrl()
        val document = client.get(targetUrl).asJsoup()
        val manga = SManga.create().apply {
            setUrlWithoutDomain(targetUrl.toString())
        }
        return parseDetails(document, manga)
    }

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        val infoElement = document.selectFirst("div.infoanime")
        val descElement = document.selectFirst("div.desc > .entry-content.entry-content-single")

        val parsedTitle = document.selectFirst("#breadcrumbs li:last-child span")?.text()
            ?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("h1.entry-title")?.text()?.removePrefix("Komik")?.trim()
        parsedTitle?.takeIf { it.isNotEmpty() }?.let { title = it }

        author = document.select(".infox .spe span:contains(Author) :not(b)").text()
        artist = document.select(".infox .spe span:contains(Artis) :not(b)").text()
        genre = infoElement?.select(".infox > .genre-info > a, .infox .spe span:contains(Jenis Komik) a")
            ?.joinToString { it.text() }
        status = parseStatus(document.selectFirst(".infox .spe span:contains(Status)")?.text())

        val pText = descElement?.select("p")?.text().orEmpty()
        description = pText.substringAfter("bercerita tentang ").ifEmpty { pText }

        thumbnail_url = document.selectFirst(".thumb > img:nth-child(1), .thumb img")?.imgAttr()
    }

    private fun parseStatus(status: String?): Int {
        val s = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "berjalan" in s || "ongoing" in s -> SManga.ONGOING
            "tamat" in s || "completed" in s -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#chapter_list li").map { element ->
        val urlElement = element.selectFirst(".lchx a")!!
        SChapter.create().apply {
            setUrlWithoutDomain(urlElement.absUrl("href"))
            name = urlElement.text()
            chapterRegex.find(name)?.let {
                chapter_number = it.groupValues[1].toFloatOrNull() ?: -1f
            }
            date_upload = element.selectFirst(".dt a")?.text()?.let { parseChapterDate(it) } ?: 0L
        }
    }

    private fun parseChapterDate(date: String): Long = if (date.contains("yang lalu")) {
        val value = date.substringBefore(' ').trim().toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()
        when {
            "detik" in date -> calendar.add(Calendar.SECOND, -value)
            "menit" in date -> calendar.add(Calendar.MINUTE, -value)
            "jam" in date -> calendar.add(Calendar.HOUR_OF_DAY, -value)
            "hari" in date -> calendar.add(Calendar.DATE, -value)
            "minggu" in date -> calendar.add(Calendar.DATE, -value * 7)
            "bulan" in date -> calendar.add(Calendar.MONTH, -value)
            "tahun" in date -> calendar.add(Calendar.YEAR, -value)
            else -> return 0L
        }
        calendar.timeInMillis
    } else {
        dateFormat.tryParseDate(date, ZoneId.of("Asia/Jakarta"))
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl + chapter.url
        val document = client.get(chapterUrl).asJsoup()
        return document.select("div:has(>img[alt*=\"Chapter\"]) img")
            .filter { it.parent()?.tagName() != "noscript" }
            .mapIndexedNotNull { i, element ->
                val onerror = element.attr("onError")
                val url = if (onerror.contains("src='")) {
                    onerror.substringAfter("src='").substringBefore("';")
                } else {
                    element.attr("data-lazy-src").ifEmpty { element.attr("src") }
                }
                if (url.isNotEmpty()) Page(i, chapterUrl, imageUrl = url) else null
            }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url.ifEmpty { "$baseUrl/" })
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList()),
    )

    // ============================= Utilities ==============================
    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div.animepost").map { mangaFromElement(it) }
        val hasNextPage = document.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.animposx > a")!!.absUrl("href"))
        title = element.selectFirst(".animposx .tt h4")!!.text()
        thumbnail_url = element.selectFirst("div.limit img")?.imgAttr()
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}
