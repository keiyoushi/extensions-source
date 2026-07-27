package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TeamLanhLung : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override fun Headers.Builder.configureHeaders() = set("Referer", "$baseUrl/")

    private fun Element.lazyImgUrl(): String? {
        val url = absUrl("data-lazy-src")
            .ifEmpty { absUrl("data-src") }
            .ifEmpty { absUrl("src").takeUnless { it.startsWith("data:") } }
            ?.ifEmpty { null }
            ?: return null
        return url.replace(smallThumbnailRegex, "$1")
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/xem-nhieu-nhat/").asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative")
            .map(::mangaFromListItem)
        return MangasPage(mangas, hasNextPage = false)
    }

    private fun mangaFromListItem(element: Element): SManga {
        val linkElement = element.selectFirst("p.super-title a[href]")!!
        return SManga.create().apply {
            title = linkElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img.list-left-img, img")?.lazyImgUrl()
        }
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return parseLatestPage(client.get(url).asJsoup())
    }

    private fun parseLatestPage(document: Document): MangasPage {
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .filter { element ->
                element.selectFirst("a[href]")?.absUrl("href").orEmpty().contains("/truyen-tranh/")
            }
            .map(::mangaFromComicItem)

        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromComicItem(element: Element): SManga {
        val linkElement = element.selectFirst(".comic-title-link a[href], a:has(h3.comic-title)")!!
        return SManga.create().apply {
            title = linkElement.selectFirst("h3.comic-title")?.text() ?: linkElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
        }
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return parseSearchResponse(client.post("$baseUrl/wp-admin/admin-ajax.php", formBody))
        }

        val filterPath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
            .ifEmpty { filters.firstInstanceOrNull<TeamFilter>()?.toUriPart().orEmpty() }
            .ifEmpty { filters.firstInstanceOrNull<SeriesFilter>()?.toUriPart().orEmpty() }
            .ifEmpty { filters.firstInstanceOrNull<KeywordFilter>()?.toUriPart().orEmpty() }

        if (filterPath.isNotEmpty()) {
            val pagePath = if (page == 1) filterPath else "${filterPath.trimEnd('/')}/page/$page/"
            return parseArchivePage(client.get(baseUrl + pagePath).asJsoup())
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchResponse(response: Response): MangasPage {
        if (response.header("Content-Type").orEmpty().contains("application/json")) {
            val searchResponse = response.parseAs<SearchResponseDto>()
            if (!searchResponse.success) return MangasPage(emptyList(), false)

            val mangas = searchResponse.data.mapNotNull { result ->
                val title = result.title ?: return@mapNotNull null
                val link = result.link?.takeIf { it.contains("/truyen-tranh/") } ?: return@mapNotNull null
                SManga.create().apply {
                    this.title = title
                    setUrlWithoutDomain(link)
                    thumbnail_url = result.img?.replace(smallThumbnailRegex, "$1")
                }
            }.distinctBy { it.url }
            return MangasPage(mangas, false)
        }

        return parseArchivePage(response.asJsoup())
    }

    private fun parseArchivePage(document: Document): MangasPage {
        val items = document.select("#archive-list-table li.position-relative")
            .ifEmpty { document.select("ul.single-list-comic li.position-relative") }
        if (items.isNotEmpty()) {
            val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
            return MangasPage(items.map(::mangaFromListItem), hasNextPage)
        }

        return parseLatestPage(document)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaUrl = if (url.pathSegments.firstOrNull() == "truyen-tranh") {
            url
        } else {
            client.get(url).asJsoup()
                .selectFirst(".breadcrumb a[href*='/truyen-tranh/']")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?: return null
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaUrl.toString()) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
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
            chapters = if (fetchChapters) parseChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h2.info-title")!!.text()
        thumbnail_url = document.selectFirst(".img-thumbnail")?.lazyImgUrl()
        author = document.selectFirst("strong:contains(Tác giả) + span")
            ?.text()
            ?.takeUnless { it.equals("Đang cập nhật", true) || it.equals("Không có", true) }
        description = parseDescription(document)
        genre = document.select(".comic-info .tags a[href*='/the-loai/']")
            .joinToString { it.text() }
            .ifEmpty { null }

        val statusString = document.selectFirst("span.comic-stt")?.text()?.lowercase()
        status = when {
            statusString?.contains("đang tiến hành") == true -> SManga.ONGOING
            statusString?.contains("trọn bộ") == true -> SManga.COMPLETED
            statusString?.contains("hoàn thành") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDescription(document: Document): String? {
        val block = document.selectFirst(".intro-container .hide-long-text") ?: return null
        val rawDescription = (block.ownText().ifEmpty { block.text() })
            .substringBefore("— Xem Thêm —")
            .trim()

        return rawDescription.removePrefix("\"").removeSuffix("\"").trim().ifEmpty { null }
    }

    // ============================== Chapters ===============================

    private suspend fun parseChapterList(firstDocument: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = firstDocument
        chapters += document.select(".chapter-table table tbody tr").mapNotNull(::parseChapterElement)

        val visited = mutableSetOf(document.location())
        var nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
            ?.absUrl("href")
            ?.ifEmpty { null }

        while (nextPage != null && visited.add(nextPage)) {
            document = client.get(nextPage).asJsoup()
            chapters += document.select(".chapter-table table tbody tr").mapNotNull(::parseChapterElement)
            nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
                ?.absUrl("href")
                ?.ifEmpty { null }
        }

        return chapters
    }

    private fun parseChapterElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize") ?: return null
        val url = linkElement.absUrl("href").takeIf { it.isNotEmpty() } ?: return null
        val isLocked = linkElement.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            val fullText = linkElement.selectFirst("span.hidden-sm.hidden-xs")?.text() ?: linkElement.text()
            val shortName = parseChapterName(fullText)
            name = if (isLocked) "🔒 $shortName" else shortName
            date_upload = element.selectFirst("td.hidden-xs.hidden-sm, td:last-child")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = chapterNameRegex.find(rawName)
        if (match != null) {
            return match.value
                .replace(chapterWordRegex, "CHAP")
                .replace(multiSpaceRegex, " ")
                .trim()
        }

        return rawName.substringAfterLast("–").substringAfterLast("-").trim()
    }

    private fun parseChapterDate(date: String): Long = runCatching {
        LocalDate.parse(date, dateFormatFull).atStartOfDay(dateZone).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(date, dateFormatShort).atStartOfDay(dateZone).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).use { response ->
        val html = response.body.string()
        val document = Jsoup.parse(html, response.request.url.toString())

        if (document.selectFirst("form.post-password-form") != null) {
            throw Exception(passwordWebViewMessage)
        }

        ImageDecryptor.extractImageUrls(html).mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return FilterData(
            genres = document.parseFilterOptions("#nav-tags"),
            teams = document.parseFilterOptions("#nav-teams"),
            series = document.parseFilterOptions("#nav-series"),
            keywords = document.parseFilterOptions("#nav-hashtags"),
        ).toJsonElement()
    }

    private fun Document.parseFilterOptions(selector: String): List<FilterOption> = select("$selector a[href]")
        .mapNotNull { element ->
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = element.absUrl("href").toHttpUrl()
            FilterOption(name, url.encodedPath)
        }
        .distinctBy { it.path }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("h3.blue-title")
            .firstOrNull { it.text().equals("Truyện liên quan", ignoreCase = true) }
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedSection.select(".comic-item").map(::mangaFromComicItem).distinctBy { it.url }
    }

    private val passwordWebViewMessage = "Vui lòng nhập mật khẩu của chương này qua webview"
    private val dateFormatFull = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateFormatShort = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterNameRegex = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
    private val chapterWordRegex = Regex("chap", RegexOption.IGNORE_CASE)
    private val multiSpaceRegex = Regex("\\s+")
    private val smallThumbnailRegex = Regex("-150x150(\\.[a-zA-Z]+)$")
}
