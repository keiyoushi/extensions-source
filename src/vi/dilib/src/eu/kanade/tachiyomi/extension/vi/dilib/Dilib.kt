package eu.kanade.tachiyomi.extension.vi.dilib

import eu.kanade.tachiyomi.network.GET
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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class Dilib : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl$searchPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("media", bookType)
            .addQueryParameter("sort", popularOrder)
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val mainCategory = filters.firstInstanceOrNull<MainCategoriesFilter>()?.toUriPart() ?: defaultMainCategory
        val subCategory = filters.firstInstanceOrNull<SubCategoriesFilter>()?.toUriPart() ?: defaultSubCategory
        val author = filters.firstInstanceOrNull<AuthorFilter>()?.state ?: ""
        val order = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: defaultOrder

        val url = "$baseUrl$searchPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("find", query)
                }
                if (mainCategory != defaultMainCategory) {
                    addQueryParameter("chinh", mainCategory)
                }
                if (subCategory != defaultSubCategory) {
                    addQueryParameter("phu", subCategory)
                }
                if (author.isNotBlank()) {
                    addQueryParameter("author", author)
                }
                if (bookType.isNotBlank()) {
                    addQueryParameter("media", bookType)
                }
                if (order != defaultOrder) {
                    addQueryParameter("sort", order)
                }
            }
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Manga List ============================

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.products.row > div.type-product").mapNotNull { element ->
            val mangaLink = element.selectFirst(".block_product_thumbnail a, .block_product_content a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".block_product_content a")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst(".block_product_thumbnail img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(".woocommerce-pagination a.pagecurrent ~ span a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val detailPath = when {
            url.pathSegments.size == 1 && url.encodedPath.endsWith(".html") -> url.encodedPath
            url.pathSegments.size == 2 &&
                url.pathSegments.first() == "truyen-tranh" &&
                "-chap-" in url.pathSegments.last() -> {
                "/${url.pathSegments.last().substringBeforeLast("-chap-")}.html"
            }
            else -> return null
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(detailPath)
        }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga
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
        val subtitle = document.selectFirst("div#content h2")?.text()
        val intro = document.selectFirst("div#content h2 + p")?.text()
        val updateTimeValue = document.selectFirst("p:contains(Cập nhật lúc)")?.ownText()
        val updateTime = updateTimeValue?.let { "Cập nhật lúc: $it" }

        title = document.selectFirst("div#primary h1")!!.text()
        thumbnail_url = document.selectFirst("div#primary .size-shop_catalog img")
            ?.absUrl("src")
            ?.normalizeImageUrl()
        genre = document.select("fieldset#pdf a.button2")
            .joinToString { it.text() }
            .ifEmpty { null }
        author = document.selectFirst("div#primary h1 + p")?.text()
        description = listOfNotNull(updateTime, subtitle, intro)
            .joinToString("\n\n")
            .ifEmpty { null }
        status = parseStatus(document.selectFirst("p:contains(Tình trạng)")?.ownText())
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        "đang cập nhật" in statusText.lowercase() -> SManga.ONGOING
        "hoàn thành" in statusText.lowercase() -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val readButton = document.selectFirst("a.button1[href*=-chap-]")
            ?: document.selectFirst("a:contains(Đọc Truyện)")

        val readUrl = readButton?.absUrl("href")
        if (readUrl.isNullOrEmpty()) return emptyList()

        val baseChapterPath = readUrl.substringBefore("-chap-")
        val chapterDocument = client.get(readUrl).asJsoup()
        val options = chapterDocument.select("select option")

        return options.mapNotNull { option ->
            val optionValue = option.attr("value")
            val chapterName = option.text()

            if (!optionValue.contains("-chap-", ignoreCase = true)) {
                return@mapNotNull null
            }

            val finalChapterUrl = "$baseChapterPath$optionValue.html"

            SChapter.create().apply {
                name = chapterName
                setUrlWithoutDomain(finalChapterUrl)
            }
        }
            .distinctBy { it.url }
            .reversed()
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        val images = document.select("div#primary > img.border")

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?: return@mapIndexedNotNull null

            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================== Helpers ===============================

    private fun String.normalizeImageUrl(): String = if (startsWith("//")) "https:$this" else this

    private val searchPath = "/search.php"
    private val listPath = "/truyen-tranh/"
    private val bookType = "5"
    private val popularOrder = "5"
    private val defaultMainCategory = ""
    private val defaultSubCategory = ""
    private val defaultOrder = "1"
}
