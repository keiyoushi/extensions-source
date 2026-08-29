package eu.kanade.tachiyomi.extension.vi.damconuong

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Source
abstract class DamCoNuong : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get(buildListUrl(page, popularSort)))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get(buildListUrl(page, latestSort)))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: latestSort
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: defaultStatus
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.id }

        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("filter[status]", status)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (!selectedGenres.isNullOrEmpty()) {
                    addQueryParameter("filter[accept_genres]", selectedGenres)
                }
            }
            .build()

        return parseMangaList(client.get(url))
    }

    private fun buildListUrl(page: Int, sort: String) = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .addQueryParameter("filter[status]", defaultStatus)
        .addQueryParameter("page", page.toString())
        .build()

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-vertical").map { element ->
            SManga.create().apply {
                val titleElement = element.selectFirst("h3 a")!!
                title = titleElement.text()
                setUrlWithoutDomain(titleElement.absUrl("href"))

                val imageElement = element.selectFirst("div.cover-frame img")
                thumbnail_url = imageElement?.absUrl("src")
                    ?.ifEmpty { imageElement.absUrl("data-src") }
                    ?.ifEmpty { null }
            }
        }

        val hasNextPage = document.selectFirst("nav[aria-label=Pagination] a[aria-label=Next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug")
        }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

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
        title = document.selectFirst("h1.text-xl.ml-1, h1.text-xl")!!.text()

        val imageElement = document.selectFirst("div.cover-frame img")
        thumbnail_url = imageElement?.absUrl("src")
            ?.ifEmpty { imageElement.absUrl("data-src") }
            ?.ifEmpty { null }

        author = document.selectFirst("span:containsOwn(Author:) + span a")?.text()
        genre = document.select("#genres-list a")
            .joinToString { it.text() }
            .ifEmpty { null }

        status = parseStatus(
            document.selectFirst("span:containsOwn(Tình trạng:)")?.parent()?.select("span")?.last()?.text(),
        )

        val descriptionElement = document.selectFirst("div.prose.dark\\:prose-invert.max-w-none")
        description = descriptionElement?.text()
            ?.ifEmpty { null }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText?.contains("Đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
        statusText?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select("#chapterList > a.block").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("div.grow span")!!.text()
            date_upload = parseChapterDate(
                element.selectFirst("span.ml-2.whitespace-nowrap")?.text(),
            )
        }
    }

    private fun parseChapterDate(dateStr: String?): Long = parseRelativeDate(dateStr)
        .takeIf { it != 0L }
        ?: dateFormat.tryParse(dateStr)

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"), Locale.ROOT)
        val number = numberRegex.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val images = document.select("#chapter-content img.chapter-img")
            .ifEmpty { document.select("#chapter-content img") }

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?: return@mapIndexedNotNull null

            Page(index, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("button")
        .mapNotNull { element ->
            val id = genreIdRegex.matchEntire(element.attr("@click"))
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("h3")
            .firstOrNull { it.text().equals("Truyện liên quan", ignoreCase = true) }
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedSection.children().mapNotNull { card ->
            val link = card.selectFirst("a[href*=/truyen/].line-clamp-2") ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
                    ?.ifEmpty { card.selectFirst("img")?.absUrl("data-src") }
                    ?.ifEmpty { null }
            }
        }.distinctBy { it.url }
    }

    private val latestSort = "-updated_at"
    private val popularSort = "-views"
    private val defaultStatus = "2,1"

    private val numberRegex = Regex("\\d+")
    private val genreIdRegex = Regex("toggleGenre\\('([^']+)'\\)")

    private val dateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
