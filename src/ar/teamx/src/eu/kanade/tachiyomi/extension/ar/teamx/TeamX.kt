package eu.kanade.tachiyomi.extension.ar.teamx

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TeamX : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(10, 1.seconds)
    }

    private val nextPageSelector = "a[rel=next]"

    private val thumbnailSuffix = "thumbnail_"

    private fun Int.pageNumber() = if (this > 1) "?page=$this" else ""

    // ============================== Popular ==============================

    private fun parsePopularManga(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("div.listupd div.bsx").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a")!!.attr("title")
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(nextPageSelector) != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/series/${page.pageNumber()}")
        return parsePopularManga(response)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl${page.pageNumber()}").asJsoup()

        val mangaList = document.select("div.last-chapter div.box")
            .mapNotNull { element ->
                SManga.create().apply {
                    val linkElement = element.selectFirst("div.info a")!!
                    title = linkElement.selectFirst("h3")!!.text()

                    setUrlWithoutDomain(linkElement.absUrl("href"))
                    thumbnail_url = element.selectFirst("div.imgu img")
                        ?.absUrl("src")
                        ?.replace(thumbnailSuffix, "")
                }
            }

        return MangasPage(mangaList, document.selectFirst(nextPageSelector) != null)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()

            val document = client.get(url).asJsoup()
            val mangas = document.select("div.tx-grid a.tx-card").map { element ->
                SManga.create().apply {
                    title = element.selectFirst("h3")!!.text()
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                        ?.replace(thumbnailSuffix, "")
                    setUrlWithoutDomain(element.absUrl("href"))
                }
            }

            return MangasPage(mangas, false)
        }

        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()

        return parsePopularManga(client.get(url))
    }

    // ============================== Deeplinking =============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size >= 2) {
            val segment = url.pathSegments[1]
            val manga = SManga.create().apply { this.url = "/series/$segment" }

            return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
                this.url = manga.url
                initialized = true
            }
        }

        throw Exception("Unsupported URL")
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl${manga.url}")
        val document = response.asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = if (fetchChapters) getChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.author-info-title h1")!!.text()

        var desc = document.select("div.review-content").text()
        if (desc.isEmpty()) {
            desc = document.select("div.review-content p").text()
        }
        description = desc

        genre = document.select("div.review-author-info a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.text-right img")?.absUrl("src")
        status = document.selectFirst(".full-list-info > small:first-child:contains(الحالة) + small")
            ?.text().toStatus()

        author = document.selectFirst(".full-list-info > small:first-child:contains(الرسام) + small")
            ?.text()
            ?.takeIf { it != "غير معروف" }
    }

    private suspend fun getChapterList(document: Document): List<SChapter> = coroutineScope {
        val firstPageChapters = parseChapterElements(document)

        val lastPage = document.select("ul.pagination a.page-link")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull() ?: 1

        if (lastPage <= 1) {
            return@coroutineScope firstPageChapters
        }

        val url = document.location()

        val remainingChapters = (2..lastPage).map { page ->
            async {
                val response = client.get("$url?page=$page")
                parseChapterElements(response.asJsoup())
            }
        }.awaitAll()

        firstPageChapters + remainingChapters.flatten()
    }

    private fun parseChapterElements(document: Document): List<SChapter> {
        return document.select("div.chapter-card").mapNotNull { element ->
            if (element.selectFirst("span.locked") != null) return@mapNotNull null
            SChapter.create().apply {
                val chpNum = element.attr("data-number")
                val chpTitle = element.selectFirst("div.chapter-info div.chapter-title")?.text()
                val invalidTitles = setOf(chpNum, "الفصل $chpNum", "الفصل رقم $chpNum")

                name = buildString {
                    append("الفصل $chpNum")
                    if (!chpTitle.isNullOrEmpty() && chpTitle !in invalidTitles) {
                        append(" - $chpTitle")
                    }
                    append("\u200F")
                }

                // data-date is Unix timestamp (seconds)
                date_upload = element.attr("data-date")
                    .toLongOrNull()
                    ?.times(1000)
                    ?: 0L

                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
    }

    override val supportsRelatedMangas = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl${chapter.url}")
        return response.asJsoup()
            .select("div.image_list canvas[data-src], div.image_list img[src]")
            .mapIndexed { i, element ->
                val url = when {
                    element.hasAttr("src") -> element.absUrl("src")
                    else -> element.absUrl("data-src")
                }
                Page(i, imageUrl = url)
            }
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/series").asJsoup()

        fun parseOptions(selector: String): Options = document.select(selector).map { FilterOption(it.text(), it.attr("value")) }

        return FilterData(
            genres = parseOptions("#select_genre option"),
            types = parseOptions("#select_type option"),
            states = parseOptions("#select_state option"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.let { it.parseAs<FilterData>() } ?: return FilterList()

        return FilterList(
            listOf(
                Filter.Header("ملاحظة: تُهمل عوامل التصفية عند البحث"),
                Filter.Separator(),
                TypeFilter(filterData.types),
                StatusFilter(filterData.states),
                GenreFilter(filterData.genres),
            ),
        )
    }
    // ============================= Utilities =============================

    private fun String?.toStatus() = when (this) {
        "مستمرة", "قادم قريبًا" -> SManga.ONGOING // "coming soon"
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
