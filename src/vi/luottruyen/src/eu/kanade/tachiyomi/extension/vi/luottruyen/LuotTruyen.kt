package eu.kanade.tachiyomi.extension.vi.luottruyen

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LuotTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(
        client.get("$baseUrl/tim-truyen?status=-1&sort=10&page=$page").asJsoup(),
    )

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(
        client.get("$baseUrl/tim-truyen-nang-cao?status=-1&sort=0&advancedSearch=true&page=$page").asJsoup(),
    )

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/tim-truyen-nang-cao".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { genre ->
                addQueryParameter("categories", genre)
                addQueryParameter("includeCategories", "true")
            }
            if (query.isNotBlank()) addQueryParameter("keyword", query)
            filters.firstInstanceOrNull<SortFilter>()?.toUriPart()?.let { addQueryParameter("sort", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let { addQueryParameter("status", it) }
            addQueryParameter("advancedSearch", "true")
            addQueryParameter("page", page.toString())
        }.build()

        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val mangaSlug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val mangaPath = when {
            url.pathSegments.size == 2 && mangaSlug.hasNumericIdSuffix() -> url.encodedPath
            url.pathSegments.size == 4 &&
                url.pathSegments[2].startsWith("chapter-") &&
                url.pathSegments[3].isNotEmpty() -> {
                client.get(url).asJsoup()
                    .select(".breadcrumb a[href]")
                    .map { it.absUrl("href").toHttpUrl() }
                    .firstOrNull {
                        it.pathSegments.size == 2 &&
                            it.pathSegments[0] == "truyen-tranh" &&
                            it.pathSegments[1].startsWith("$mangaSlug-") &&
                            it.pathSegments[1].hasNumericIdSuffix()
                    }
                    ?.encodedPath
            }
            else -> null
        } ?: return null

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangaList = document.select("[id^=ctl00_divCenter] .row > .item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("figcaption h3 a, h3 a, a.jtip")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("div.image a img, div.image img")?.absUrl("src")
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("li.next:not(.disabled) a, li:not(.disabled).next a") != null
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = if (fetchDetails) {
            async { mangaDetailsParse(client.get("$baseUrl${manga.url}").asJsoup(), manga) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { parseChapterList(client.post("$baseUrl/Story/ListChapterByStoryID", chapterHeaders, chapterBody(manga))) }
        } else {
            null
        }

        SMangaUpdate(
            manga = mangaDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("article#item-detail h1.title-detail, article#item-detail h1, h1.title-detail")!!.text()
        document.selectFirst("article#item-detail")?.let { infoElement ->
            author = infoElement.selectFirst("li.author p.col-xs-8, li.author p.col-xs-10")?.text()
            status = infoElement.selectFirst("li.status p.col-xs-8, li.status p.col-xs-10")?.text().toStatus()
            genre = infoElement.select("li.kind p.col-xs-8 a, li.kind p.col-xs-12 a").joinToString { it.text() }
            description = infoElement.select("div.detail-content p").joinToString("\n") { it.text() }
            thumbnail_url = infoElement.selectFirst("div.col-image img")?.absUrl("src")
        }
    }

    private fun String?.toStatus(): Int {
        val value = this?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in value || "đang cập nhật" in value -> SManga.ONGOING
            "hoàn thành" in value -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun chapterBody(manga: SManga) = FormBody.Builder()
        .add("StoryID", manga.url.substringAfterLast("-"))
        .build()

    private val chapterHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()

    private fun parseChapterList(response: Response): List<SChapter> = response.asJsoup()
        .select("li.row:not(.heading)").mapNotNull { element ->
            val chapterLinkElement: Element = element.selectFirst("div.chapter a, a") ?: return@mapNotNull null
            SChapter.create().apply {
                name = chapterLinkElement.text()
                setUrlWithoutDomain(chapterLinkElement.absUrl("href"))
                date_upload = parseRelativeDate(element.selectFirst("div.col-xs-4")?.text())
            }
        }

    private fun parseRelativeDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L
        val number = relativeDateNumberRegex.find(date)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in date -> number.seconds
            "phút" in date -> number.minutes
            "giờ" in date -> number.hours
            "ngày" in date -> number.days
            "tuần" in date -> (number * 7).days
            "tháng" in date -> (number * 30).days
            "năm" in date -> (number * 365).days
            else -> return 0L
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val images = document.select("#view-chapter img")
            .ifEmpty {
                document.select(".chapter-content img, .reading-content img, .content-chapter img, .reading-detail .page-chapter img[data-index]")
            }

        if (images.isEmpty()) return emptyList()

        return images.mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-truyen-nang-cao").asJsoup()
        .select("input[name=categories]")
        .mapNotNull { input ->
            val name = input.parent()?.selectFirst("label[for=${input.id()}]")?.text()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val value = input.attr("value").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(name, value)
        }
        .distinctBy { it.value }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private fun String.hasNumericIdSuffix(): Boolean = substringAfterLast('-', missingDelimiterValue = "").let { it.isNotEmpty() && it.all(Char::isDigit) }

    private val relativeDateNumberRegex = Regex("""\d+""")
}
