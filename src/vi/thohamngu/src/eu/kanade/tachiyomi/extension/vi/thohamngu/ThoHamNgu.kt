package eu.kanade.tachiyomi.extension.vi.thohamngu

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ThoHamNgu : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }
    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/nhieu-xem-nhat/").asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("p.super-title a")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img.list-left-img")?.lazyImgUrl()
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return parseLatestPage(client.get(url).asJsoup())
    }

    private fun parseLatestPage(document: Document): MangasPage {
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .filter { element ->
                val href = element.selectFirst("a")?.absUrl("href").orEmpty()
                href.contains("/truyen/")
            }
            .map { element ->
                SManga.create().apply {
                    title = element.selectFirst("h3.comic-title")!!.text()
                    setUrlWithoutDomain(element.selectFirst("h3.comic-title")!!.parent()!!.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
                }
            }
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return parseSearchApiResponse(client.post("$baseUrl/wp-admin/admin-ajax.php", formBody))
        }

        val filterPath = filters.firstInstanceOrNull<UriPartFilter>()
            ?.toUriPart()
        if (filterPath != null) {
            return parseFilterPage(client.get(baseUrl.toHttpUrl().resolve(filterPath)!!).asJsoup())
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { it.link.contains("/truyen/") }
            .map { result ->
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(result.link)
                    thumbnail_url = result.img?.replace(smallThumbnailRegex, "$1")
                }
            }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseFilterPage(document: Document): MangasPage {
        val items = document.select("ul.single-list-comic li.position-relative")
        if (items.isEmpty()) return parseLatestPage(document)

        val mangas = items.map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("p.super-title a")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img.list-left-img")?.lazyImgUrl()
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val document = client.get(url).asJsoup()
        val mangaUrl = if (url.pathSegments.firstOrNull() == "truyen") {
            url
        } else {
            document.selectFirst(".breadcrumb a[href*=/truyen/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?: return null
        }
        val manga = SManga.create().apply {
            setUrlWithoutDomain(mangaUrl.toString())
        }
        return if (mangaUrl == url) {
            parseMangaDetails(document, manga)
        } else {
            fetchMangaUpdate(manga, emptyList(), true, false).manga
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
        title = document.selectFirst("h2.info-title")!!.text()
        thumbnail_url = document.selectFirst("div.col-sm-4 img.img-thumbnail")?.lazyImgUrl()
        author = document.selectFirst("strong:contains(Tác giả) + span")?.text()
        status = document.selectFirst("span.comic-stt")?.text()
            ?.let { parseStatus(it) }
            ?: SManga.UNKNOWN
        genre = document.select("a[href*=/the-loai/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst("div.text-justify")?.text()
    }

    private fun parseStatus(status: String): Int {
        val normalizedStatus = status.lowercase()
        return when {
            "đang tiến hành" in normalizedStatus -> SManga.ONGOING
            "hoàn thành" in normalizedStatus || "trọn bộ" in normalizedStatus -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters =============================

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select(".table-scroll table tr").mapNotNull { row ->
            val linkElement = row.selectFirst("a.text-capitalize") ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = parseChapterName(linkElement.text())
                date_upload = row.selectFirst("td.hidden-xs.hidden-sm")?.text()
                    ?.let { parseChapterDate(it) }
                    ?: 0L
            }
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = chapterNameRegex.find(rawName)
        return match?.value?.trim() ?: rawName.substringAfterLast("–").substringAfterLast("-").trim()
    }

    private fun parseChapterDate(dateStr: String): Long = dateFormat.tryParseDate(dateStr, dateZone)

    // ============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageUrls = extractImageUrls(document)

        return imageUrls.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    private fun extractImageUrls(document: Document): List<String> {
        val viewChapter = document.selectFirst("#view-chapter") ?: document

        return viewChapter.select("img").mapNotNull { img ->
            val lazySrc = img.attr("data-lazy-src")
            if (lazySrc.isNotBlank() && lazySrc.startsWith("http")) {
                return@mapNotNull lazySrc
            }

            val src = img.attr("abs:src")
            if (src.isNotBlank() && src.startsWith("http") && !src.startsWith("data:")) {
                return@mapNotNull src
            }

            null
        }.distinct()
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup().let { document ->
        FilterData(
            genres = document.parseFilterOptions("#nav-tags"),
            groups = document.parseFilterOptions("#nav-teams"),
            series = document.parseFilterOptions("#nav-series"),
            keywords = document.parseFilterOptions("#nav-hashtags"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private fun Document.parseFilterOptions(selector: String): List<FilterOption> = select("$selector a[href]").mapNotNull { element ->
        val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val path = element.absUrl("href").toHttpUrl().encodedPath
        FilterOption(name, path)
    }.distinctBy { it.path }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("h3.blue-title")
            .firstOrNull { it.text() == "Truyện liên quan" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select(".comic-item-box").mapNotNull { element ->
            val link = element.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
            val title = link.attr("title").takeIf { it.isNotEmpty() }
                ?: element.selectFirst(".comic-title")?.text()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
            }
        }.distinctBy { it.url }
    }

    // ============================= Utilities =============================

    private fun Element.lazyImgUrl(): String? {
        val url = absUrl("data-lazy-src")
            .ifEmpty { absUrl("src").takeUnless { it.startsWith("data:") } }
            ?.ifEmpty { null }
            ?: return null
        return url.replace(smallThumbnailRegex, "$1")
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterNameRegex = Regex("Chap\\s*\\d+(\\.\\d+)?", RegexOption.IGNORE_CASE)
    private val smallThumbnailRegex = Regex("-150x150(\\.[a-zA-Z]+)$")
}
