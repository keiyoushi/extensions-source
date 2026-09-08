package eu.kanade.tachiyomi.extension.vi.truyenhentaivn

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenHentaivn : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listPageUrl("/top-de-cu", page)))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get(listPageUrl("/danh-sach", page)))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

        val url = if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem-truyen/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            url
        } else if (genrePath != null) {
            listPageUrl(genrePath, page)
        } else {
            listPageUrl("/danh-sach", page)
        }

        return mangaListParse(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val path = url.encodedPath
        val detailUrl = when {
            detailPathRegex.matches(path) -> url
            chapterPathRegex.matches(path) -> client.get(url).asJsoup()
                .selectFirst("a[href*=-doc-truyen-]")
                ?.absUrl("href")
                ?.toHttpUrl()
            else -> null
        } ?: return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain(detailUrl.toString())
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private fun listPageUrl(path: String, page: Int): HttpUrl = "$baseUrl$path".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .build()

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.entry.text-center").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".z-pagination a.page-numbers[title=Next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("a.name")!!
        setUrlWithoutDomain(titleElement.absUrl("href"))
        title = titleElement.attr("title").ifEmpty { titleElement.text() }
        thumbnail_url = element.selectFirst("a.s-thumb img")?.absUrl("src")
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst(".comic-info .info h1.name")!!.text()
        author = document.selectFirst(".meta-data .author i")
            ?.text()
            ?.ifEmpty { null }
        genre = document.select(".meta-data .genre a")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst(".comic-description .inner")
            ?.text()
            ?.ifEmpty { null }
        status = parseStatus(
            document.select(".tsinfo .imptdt")
                .firstOrNull { it.text().contains("Tình trạng") }
                ?.selectFirst("i")
                ?.text(),
        )
        thumbnail_url = document.selectFirst(".comic-info .book img")?.absUrl("src")
    }

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized -> SManga.COMPLETED
            "đang tiến hành" in normalized || "đang cập nhật" in normalized -> SManga.ONGOING
            "tạm ngưng" in normalized || "tạm dừng" in normalized || "hiatus" in normalized -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document): List<SChapter> = document
        .select(".chap-list a.d-flex.justify-content-between")
        .map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.name")!!.text()
                date_upload = parseDate(element.select("span").getOrNull(1)?.text())
            }
        }

    private fun parseDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDate.parse(date, dateFormat)
                .atStartOfDay(vietnamZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val imageUrls = document.select(".chapter-content img")
            .map { imageElement ->
                imageElement.absUrl("data-src").ifEmpty { imageElement.absUrl("src") }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .ifEmpty {
                document.select(".content-text img")
                    .map { imageElement ->
                        imageElement.absUrl("data-src").ifEmpty { imageElement.absUrl("src") }
                    }
                    .filter { imageUrl ->
                        imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
                    }
            }
            .distinct()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("li:has(> a[href=/the-loai-truyen/]) ul.sub-menu a[href^=/the-loai-]")
        .mapNotNull { link ->
            val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val path = link.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
            GenreOption(name, path)
        }
        .distinctBy { it.path }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("div.releases")
            .firstOrNull { it.selectFirst("h2")?.text() == "Truyện Hentai liên quan" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select("div.entry.text-center")
            .map(::mangaFromElement)
            .distinctBy { it.url }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val detailPathRegex = Regex("""/\d+-doc-truyen-.+\.html""")
    private val chapterPathRegex = Regex("""/\d+-\d+-xem-truyen-.+\.html""")
}
