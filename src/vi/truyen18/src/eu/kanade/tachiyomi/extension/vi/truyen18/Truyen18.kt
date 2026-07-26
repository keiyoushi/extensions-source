package eu.kanade.tachiyomi.extension.vi.truyen18

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
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Truyen18 : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/xem-nhieu-nhat", page)))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/moi-cap-nhat", page)))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            val searchPath = if (page > 1) "/search/page/$page" else "/search"
            "$baseUrl$searchPath".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        } else {
            val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
                ?: return getPopularManga(page)
            buildPagedUrl("/category/$genreSlug", page).toHttpUrl()
        }

        return parseMangaPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "doc-truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/doc-truyen/$slug")
        }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("main a[href^=/doc-truyen]:has(h3)")
            .map { parseMangaElement(it) }
            .distinctBy { it.url }
        return MangasPage(mangaList, document.selectFirst("link[rel=next]") != null)
    }

    private fun parseMangaElement(mangaElement: Element): SManga = SManga.create().apply {
        val mangaPath = mangaElement.attr("href")
        val card = mangaElement.parents().firstOrNull { parent ->
            parent.select("a[href]").any { link ->
                link.attr("href") == mangaPath && link.selectFirst("img[src]") != null
            }
        }

        title = mangaElement.selectFirst("h3")!!.text()
        setUrlWithoutDomain(mangaElement.absUrl("href"))
        thumbnail_url = card
            ?.select("a[href]")
            ?.firstOrNull { link -> link.attr("href") == mangaPath && link.selectFirst("img[src]") != null }
            ?.selectFirst("img[src]")
            ?.extractImageUrl()
    }

    private fun Element.extractImageUrl(): String? {
        val rawUrl = absUrl("src")
        if (rawUrl.isEmpty()) return null

        val imageParam = rawUrl.toHttpUrlOrNull()?.queryParameter("url") ?: return rawUrl
        return if (imageParam.startsWith("http")) {
            imageParam
        } else {
            "$baseUrl${if (imageParam.startsWith('/')) imageParam else "/$imageParam"}"
        }
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
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
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("main h1")!!.text()
        thumbnail_url = document.selectFirst("main img[alt][src]")?.extractImageUrl()
        author = findInfoValue(document, "Tác giả")
        status = parseStatus(findInfoValue(document, "Trạng thái"))
        genre = parseGenres(document)
        description = document.select("main p")
            .firstOrNull { it.text().startsWith("Nội dung cập nhật") }
            ?.text()
    }

    private fun parseGenres(document: Document): String? {
        val genres = document.select("main a[href*='/category/'], main a[href*='/tag/']")
            .map(Element::text)
            .distinct()

        return genres.takeIf { it.isNotEmpty() }?.joinToString()
    }

    private fun findInfoValue(document: Document, label: String): String? {
        val infoRow = document.select("main span")
            .firstOrNull { it.text().equals("$label:", ignoreCase = true) }
            ?.parent()
            ?: return null

        return infoRow.children().getOrNull(1)?.text()
    }

    private fun parseStatus(statusText: String?): Int {
        val status = statusText?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status || "đang cập nhật" in status -> SManga.ONGOING
            "hoàn thành" in status -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapterSection = document.select("main h2")
            .firstOrNull { it.text().contains("Danh sách chương") }
            ?.closest("section")
            ?: return emptyList()

        return chapterSection.select("a[href*=/chapter-]")
            .distinctBy { it.absUrl("href") }
            .map { chapterLink ->
                val chapterRow = chapterLink.parents().firstOrNull { "Đăng lúc:" in it.text() }
                SChapter.create().apply {
                    name = chapterLink.attr("title").substringAfterLast(" - ").ifBlank { chapterLink.text() }
                    setUrlWithoutDomain(chapterLink.absUrl("href"))
                    date_upload = parseChapterDate(chapterRow?.text()?.substringAfter("Đăng lúc:"))
                }
            }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val relativeDate = parseRelativeDate(dateText.lowercase())
        if (relativeDate != 0L) return relativeDate

        return runCatching {
            LocalDate.parse(dateText, chapterDateFormat)
                .atStartOfDay(vietnamZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun parseRelativeDate(dateText: String): Long {
        if ("vừa xong" in dateText) return Clock.System.now().toEpochMilliseconds()

        val amount = dateNumberRegex.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in dateText -> amount.seconds
            "phút" in dateText -> amount.minutes
            "giờ" in dateText -> amount.hours
            "ngày" in dateText -> amount.days
            "tuần" in dateText -> (amount * 7).days
            "tháng" in dateText -> (amount * 30).days
            "năm" in dateText -> (amount * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // ============================== Pages =================================

    @Serializable
    private class ReaderChapter(
        val slug: String,
        val content: String,
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterSlug = getChapterUrl(chapter).toHttpUrl().pathSegments.lastOrNull()
            ?: return emptyList()
        val chapterData = client.get(getChapterUrl(chapter)).asJsoup()
            .extractReaderChapter(chapterSlug)
            ?: return emptyList()

        return Jsoup.parseBodyFragment(chapterData.content, baseUrl)
            .select("img[src]")
            .filterNot { image -> image.absUrl("src").endsWith("/bn.png") }
            .mapIndexed { index, image ->
                Page(index, imageUrl = image.absUrl("src"))
            }
    }

    private fun Document.extractReaderChapter(chapterSlug: String): ReaderChapter? {
        val rscBody = select("script:not([src])")
            .mapNotNull { script ->
                val data = script.data()
                if (!data.startsWith(nextFlightPrefix)) return@mapNotNull null

                runCatching {
                    data.substring(nextFlightPrefix.length, data.lastIndexOf(')'))
                        .parseAs<JsonArray>()
                        .getOrNull(1)
                        ?.stringOrNull
                }.getOrNull()
            }
            .joinToString("")

        return rscBody.extractNextJsRsc<ReaderChapter> { element ->
            element is JsonObject &&
                element.getStringOrNull("slug") == chapterSlug &&
                !element.getStringOrNull("content").isNullOrBlank()
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        val categoryList = document.select("h2")
            .firstOrNull { it.text().contains("Danh sách thể loại") }
            ?.parent()
            ?.nextElementSibling()

        return categoryList
            ?.select("a[href*=/category/]")
            .orEmpty()
            .mapNotNull { link ->
                val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val slug = link.absUrl("href").toHttpUrlOrNull()?.pathSegments?.getOrNull(1)
                    ?: return@mapNotNull null
                GenreOption(name, slug)
            }
            .distinctBy { it.slug }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("h2")
            .firstOrNull { it.text().contains("Bạn có thể đọc thêm") }
            ?.closest("section")
            ?: return emptyList()

        return relatedSection.select("a[href^=/doc-truyen]:has(h3)")
            .map { parseMangaElement(it) }
            .distinctBy { it.url }
    }

    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val dateNumberRegex = Regex("\\d+")
    private val nextFlightPrefix = "self.__next_f.push("
}
