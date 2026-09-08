package eu.kanade.tachiyomi.extension.vi.thienthaitruyen

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
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ThienThaiTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(
        page = page,
        query = "",
        filters = FilterList(StatusFilter(), SortFilter().apply { state = 1 }),
    )

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaElements = document.selectFirst("form#filters-container")
            ?.parent()
            ?.nextElementSibling()
            ?.select("a[href*=/truyen-tranh/]")
            ?.filter { it.selectFirst("span.line-clamp-2") != null }
            .orEmpty()

        val mangas = mangaElements
            .ifEmpty {
                document.select("a[href*=/truyen-tranh/]")
                    .filter { it.selectFirst("span.line-clamp-2") != null && it.selectFirst("img[src]") != null }
            }
            .map(::mangaFromElement)

        val hasNextPage = document.select("a[href*=page=]")
            .any { it.text().contains("Sau") }

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("span.line-clamp-2")!!.text()
        thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
    }

    // ============================== Latest ================================
    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(
        page = page,
        query = "",
        filters = FilterList(StatusFilter(), SortFilter()),
    )

    // ============================== Search ================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters)

    private suspend fun getMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("name", query)
            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter(Genre::state)
                ?.forEach { addQueryParameter("genres[]", it.value) }
            addQueryParameter("status", filters.firstInstanceOrNull<StatusFilter>()?.selected ?: "all")
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.selected ?: "latest")
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaListPage(client.get(url).asJsoup())
    }

    // ============================== Details ===============================
    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1")!!.text()
        author = document.infoValue("Tác giả")
        genre = document.select("h3:containsOwn(Thể loại) + div a[href*=/the-loai/]")
            .map(Element::text)
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = parseStatus(document.infoValue("Trạng thái"))
        description = document.selectFirst("p.comic-content.desk, p.comic-content.mobile, p.comic-content")
            ?.text()
        thumbnail_url = document.selectFirst("img[alt=poster]")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun Document.infoValue(label: String): String? = select("p, h3")
        .firstOrNull { it.text() == label && it.parents().none { parent -> parent.tagName() == "a" } }
        ?.nextElementSibling()
        ?.text()

    private fun parseStatus(status: String?): Int {
        val normalizedStatus = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang ra" in normalizedStatus -> SManga.ONGOING
            "hoàn thành" in normalizedStatus -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.chapter-items > a.flex.justify-between.items-center.w-full")
        .mapNotNull { element ->
            val url = element.absUrl("href").takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val name = element.selectFirst("p.text-sm.text-white.font-medium")
                ?.text()
                ?.takeIf(String::isNotEmpty)
                ?: element.text().takeIf(String::isNotEmpty)
                ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(url)
                this.name = name
                date_upload = parseChapterDate(element.selectFirst("p.text-xs span")?.text())
            }
        }

    private fun parseChapterDate(dateStr: String?): Long {
        val relativeDate = parseRelativeDate(dateStr)
        if (relativeDate != 0L) return relativeDate
        return runCatching {
            LocalDate.parse(dateStr, dateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val amount = numberRegex.find(dateStr)?.value?.toLongOrNull() ?: return 0L
        val date = when {
            "giây" in dateStr -> ZonedDateTime.now(dateZone).minusSeconds(amount)
            "phút" in dateStr -> ZonedDateTime.now(dateZone).minusMinutes(amount)
            "giờ" in dateStr -> ZonedDateTime.now(dateZone).minusHours(amount)
            "ngày" in dateStr -> ZonedDateTime.now(dateZone).minusDays(amount)
            "tuần" in dateStr -> ZonedDateTime.now(dateZone).minusWeeks(amount)
            "tháng" in dateStr -> ZonedDateTime.now(dateZone).minusMonths(amount)
            "năm" in dateStr -> ZonedDateTime.now(dateZone).minusYears(amount)
            else -> return 0L
        }

        return date.toInstant().toEpochMilli()
    }

    // ============================== Pages =================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

        val imageUrls = document
            .select("div.w-full.mx-auto.center img:not([title=banner])")
            .ifEmpty { document.select("div.center img:not([title=banner])") }
            .mapNotNull { image ->
                image.absUrl("src")
                    .takeIf(String::isNotBlank)
                    ?.takeUnless { it.contains("/banner/") }
            }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem-nang-cao").asJsoup()
        .select("#genres-filter input[name='genres[]'][value]")
        .mapNotNull { input ->
            val name = input.parent()?.text()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            GenreOption(name, input.attr("value"))
        }
        .distinctBy { it.value }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================
    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val relatedContainer = document.select("h2")
            .firstOrNull { it.text() == "Liên quan" }
            ?.parent()
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedContainer.children().mapNotNull { card ->
            val link = card.selectFirst("a[href*=/truyen-tranh/]") ?: return@mapNotNull null
            val title = link.attr("title").ifEmpty {
                card.selectFirst("h3")?.text().orEmpty()
            }.takeIf(String::isNotEmpty) ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst("img[src]")?.absUrl("src")
            }
        }.distinctBy { it.url }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val numberRegex = Regex("""\d+""")
}
