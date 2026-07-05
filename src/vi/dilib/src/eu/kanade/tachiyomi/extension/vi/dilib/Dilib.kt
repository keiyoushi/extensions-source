package eu.kanade.tachiyomi.extension.vi.dilib

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Dilib : HttpSource() {
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl$LIST_PATH")

    override val client = network.client.newBuilder()
        .connectTimeout(30.seconds)
        .readTimeout(60.seconds)
        .rateLimit(3)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl$SEARCH_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("media", BOOK_TYPE)
            .addQueryParameter("sort", POPULAR_ORDER)
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }

        val mainCategory = filterList.firstInstanceOrNull<MainCategoriesFilter>()?.toUriPart() ?: DEFAULT_MAINCATEGORY
        val subCategory = filterList.firstInstanceOrNull<SubCategoriesFilter>()?.toUriPart() ?: DEFAULT_SUBCATEGORY
        val author = filterList.firstInstanceOrNull<AuthorFilter>()?.state ?: ""
        val bookType = BOOK_TYPE
        val order = filterList.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: DEFAULT_ORDER

        val url = "$baseUrl$SEARCH_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("find", query)
                }
                if (mainCategory != DEFAULT_MAINCATEGORY) {
                    addQueryParameter("chinh", mainCategory)
                }
                if (subCategory != DEFAULT_SUBCATEGORY) {
                    addQueryParameter("phu", subCategory)
                }
                if (author.isNotBlank()) {
                    addQueryParameter("author", author)
                }
                if (bookType.isNotBlank()) {
                    addQueryParameter("media", bookType)
                }
                if (order != DEFAULT_ORDER) {
                    addQueryParameter("sort", order)
                }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseBrowsePage(response)

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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val subtitle = document.selectFirst("div#content h2")?.text()
        val intro = document.selectFirst("div#content h2 + p")?.text()
        val updateTimeValue = document.selectFirst("p:contains(Cập nhật lúc)")?.ownText()?.trim()
        val updateTime = updateTimeValue?.let { "Cập nhật lúc: $it" }
        val statusText = document.selectFirst("p:contains(Tình trạng)")?.ownText()?.trim() ?: ""

        return SManga.create().apply {
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
                .trim()
            status = parseStatus(
                document.selectFirst("p:contains(Tình trạng)")?.ownText()?.trim() ?: "",
            )
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText?.contains("Đang cập nhật", ignoreCase = true) == true -> SManga.ONGOING
        statusText?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val readButton = document.selectFirst("a.button1[href*=-chap-]")
            ?: document.selectFirst("a:contains(Đọc Truyện)")

        val readUrl = readButton?.attr("href")
        if (readUrl.isNullOrEmpty()) return emptyList()

        val baseChapterPath = readUrl.substringBefore("-chap-")

        val chapterRequest = GET(baseUrl + readUrl, headers)
        val chapterResponse = client.newCall(chapterRequest).execute()
        val chapterDocument = chapterResponse.asJsoup()

        val options = chapterDocument.select("select option")

        return options.mapNotNull { option ->
            val optionValue = option.attr("value")
            val chapterName = option.text().trim()

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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div#primary > img.border")

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?: return@mapIndexedNotNull null

            Page(index, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Helpers ===============================

    private fun String.normalizeImageUrl(): String = if (startsWith("//")) "https:$this" else this

    companion object {
        private const val SEARCH_PATH = "/search.php"
        private const val LIST_PATH = "/truyen-tranh/"
        private const val BOOK_TYPE = "5"
        private const val POPULAR_ORDER = "5"
        private const val DEFAULT_MAINCATEGORY = ""
        private const val DEFAULT_SUBCATEGORY = ""
        private const val DEFAULT_ORDER = "1"
    }
}
