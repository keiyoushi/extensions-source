package eu.kanade.tachiyomi.extension.vi.truyenmm
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Source
abstract class TruyenMM : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = parseMangaListPage(
        client.get("$baseUrl/danh-sach-truyen/$page"),
        page,
    )

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int) = parseMangaListPage(
        client.get("$baseUrl/truyen-moi-cap-nhat/$page"),
        page,
    )

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("page", page.toString())
                .build().toString()
        } else {
            val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
                ?: return getPopularManga(page)
            "$baseUrl/the-loai/$genreSlug/$page"
        }
        return parseMangaListPage(client.get(url), page)
    }

    private fun parseMangaListPage(response: Response, page: Int): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("article:has(a[href^=/truyen/])").map { element ->
            SManga.create().apply {
                val mangaLink = element.selectFirst("a[href^=/truyen/]")!!
                title = element.selectFirst("h2, h3")!!.text()
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.extractImageUrl()
            }
        }
        return MangasPage(mangaList, document.selectFirst("a[href*='${page + 1}']") != null)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(baseUrl).asJsoup()
        .select("#h-genre a[href*=/the-loai/]").associate {
            it.text() to it.attr("href").split("/")[2]
        }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs())

    // ============================== Details ===============================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            parseDetails(doc),
            if (fetchChapters) parseChapters(doc) else chapters,
        )
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img[alt*=Bìa], img[alt*=bìa]")?.extractImageUrl()

        author = findInfoValue(document, "Tác giả")
        status = parseStatus(findInfoValue(document, "Loại Truyện"))
        genre = document.select("dd a[href*='/the-loai/']")
            .map(Element::text)
            .distinct()
            .joinToString()
            .ifEmpty { null }
    }

    private fun findInfoValue(document: Document, label: String): String? = document.select("dl > div").firstOrNull {
        it.selectFirst("dt")?.text()?.startsWith(label, ignoreCase = true) == true
    }?.selectFirst("dd")?.text()?.ifEmpty { null }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun parseChapters(document: Document): List<SChapter> {
        val topicId = document.selectFirst("script#script-chapter")?.attr("data-id")
        val topic = topicId?.let { fetchTopic(it) }
        if (topic != null) {
            return topic.chapters.orEmpty().mapNotNull { chapter ->
                val chapterId = chapter.id ?: return@mapNotNull null
                val chapterName = chapter.name ?: return@mapNotNull null
                val chapterUrl = getChapterUrl(chapterId)

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = chapterName
                    date_upload = chapter.update_time ?: 0L
                }
            }
        }

        return document.select("#chapter-list a[href*='/chapter-']").map { chapterElement ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterElement.absUrl("href"))
                name = chapterElement.selectFirst("span")?.text() ?: chapterElement.text()
                date_upload = parseChapterDate(chapterElement.selectFirst("time")?.text())
            }
        }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText == null) return 0L
        val normalized = dateText.replace("🗓", "")
        return dateFormat.tryParseDate(normalized).takeIf { it != 0L }
            ?: parseRelativeDate(normalized)
    }

    private fun parseRelativeDate(dateText: String): Long {
        if (dateText.contains("vừa xong", ignoreCase = true)) return 0L

        val now = ZonedDateTime.now(VIETNAM_ZONE)

        val number = DATE_NUMBER_REGEX.find(dateText)?.value?.toLongOrNull() ?: return 0L

        val date = when {
            dateText.contains("giây", ignoreCase = true) -> now.minusSeconds(number)
            dateText.contains("phút", ignoreCase = true) -> now.minusMinutes(number)
            dateText.contains("giờ", ignoreCase = true) -> now.minusHours(number)
            dateText.contains("ngày", ignoreCase = true) -> now.minusDays(number)
            dateText.contains("tuần", ignoreCase = true) -> now.minusWeeks(number)
            dateText.contains("tháng", ignoreCase = true) -> now.minusMonths(number)
            dateText.contains("năm", ignoreCase = true) -> now.minusYears(number)
            else -> return 0L
        }

        return date.toInstant().toEpochMilli()
    }

    private suspend fun fetchTopic(topicId: String): TruyenMMTopic? {
        val url = "$baseUrl/api/get-topic".toHttpUrl().newBuilder()
            .addQueryParameter("id", topicId)
            .build()

        return runCatching {
            client.get(url)
                .parseAs<TruyenMMGetTopicResponse>()
                .topic
        }.getOrNull()
    }

    private fun getChapterUrl(rawChapterId: String): String {
        val normalizedChapterId = rawChapterId.replace("-chapter-", "/chapter-")
        val splitIndex = normalizedChapterId.indexOf("/chapter-")
        if (splitIndex == -1) {
            return "$baseUrl/truyen/$normalizedChapterId"
        }

        val mangaId = normalizedChapterId.substring(0, splitIndex)
        val chapterId = normalizedChapterId.substring(splitIndex + 1)
        return "$baseUrl/truyen/$mangaId/$chapterId"
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter))
            .asJsoup()

        val imageUrls = document.select("div.w-full.flex.flex-col.items-center img").ifEmpty {
            document.select("img[data-src], img[src]")
        }.mapNotNull { imageElement ->
            imageElement.extractImageUrl()
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { url ->
                    !url.contains("/chapter-") &&
                        !url.endsWith("/loading.webp") &&
                        !url.endsWith("/page_logo.png")
                }
        }.distinct()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }
    private fun Element.extractImageUrl(): String? {
        val dataSrc = attr("data-src")
        if (dataSrc.isNotBlank()) {
            return absUrl("data-src")
        }

        val src = attr("src")
        if (src.isNotBlank()) {
            return absUrl("src")
        }

        return null
    }

    private companion object {
        val DATE_NUMBER_REGEX = Regex("""\d+""")
        val VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(VIETNAM_ZONE)
    }
}
