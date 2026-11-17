package eu.kanade.tachiyomi.extension.vi.hangtruyenonline

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HangTruyenOnline : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = searchRequest(page) {
        addQueryParameter("orderBy", "view_desc")
        addQueryParameter("sort", "view_desc")
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchRequest(page) {
        addQueryParameter("orderBy", "udpated_at_date_desc")
        addQueryParameter("sort", "udpated_at_date_desc")
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = searchRequest(page) {
        if (query.isNotBlank()) addQueryParameter("keyword", query)

        filters.firstInstanceOrNull<CategoryFilter>()?.selectedIds()
            ?.takeIf { it.isNotEmpty() }
            ?.let { addQueryParameter("categoryIds", it) }

        filters.firstInstanceOrNull<TagFilter>()?.selectedIds()
            ?.takeIf { it.isNotEmpty() }
            ?.let { addQueryParameter("genreIds", it) }

        filters.firstInstanceOrNull<SortFilter>()?.let {
            addQueryParameter("orderBy", it.toUriPart())
            addQueryParameter("sort", it.toUriPart())
        }
    }

    private suspend fun searchRequest(
        page: Int,
        params: HttpUrl.Builder.() -> Unit,
    ): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply(params)
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".m-post").mapNotNull { it.toSManga() }

        val countPage = document.selectFirst(".pagination")
            ?.attrOrNull("data-count-page")
            ?.toIntOrNull()
        val hasNextPage = if (countPage != null) {
            page < countPage
        } else {
            document.selectFirst(".pagination a[href*=\"page=${page + 1}\"]") != null
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga(): SManga? {
        val link = selectFirst(".m-name a[href*=\"/truyen-tranh/\"]")
            ?: selectFirst("a[href*=\"/truyen-tranh/\"]")
            ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = selectFirst("img")?.let { imageUrl(it) }
        }
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)

        val detail = document.extractJson<MangaDetailDto>("window.mangaDetail")

        title = detail?.title
            ?: document.selectFirst("h1.title-detail")!!.text()

        author = detail?.author?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".info-group .author p")?.text()
                ?.takeIf { it.isNotBlank() }

        genre = detail?.genres?.joinToString { it.name }?.takeIf { it.isNotEmpty() }
            ?: document.select(".m-tags a").joinToString { it.text() }
                .takeIf { it.isNotEmpty() }

        description = detail?.overview?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".sort-des .line-clamp")?.text()
                ?.takeIf { it.isNotBlank() }

        thumbnail_url = detail?.posterPath
            ?: document.selectFirst(".col-image img")?.let { imageUrl(it) }

        status = document.selectFirst(".info-group .status p")?.text()?.lowercase().let { statusText ->
            when {
                statusText == null -> SManga.UNKNOWN
                "đang tiến hành" in statusText -> SManga.ONGOING
                "hoàn thành" in statusText -> SManga.COMPLETED
                "tạm ngưng" in statusText || "ngừng" in statusText -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments
        if (segments.firstOrNull() != "truyen-tranh") return null
        val slug = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val mangaUrl = segments.getOrNull(2)
            ?.takeIf { it.startsWith("chapter-") }
            ?.let { "$baseUrl/truyen-tranh/$slug" }
            ?: url.toString()

        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return parseMangaDetails(document, manga)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    // ============================= Chapters ===============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".l-chapter").mapNotNull { element ->
        val link = element.selectFirst("a.ll-chap[href]") ?: return@mapNotNull null
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.attrOrNull("title") ?: link.text()
            date_upload = parseRelativeDate(
                element.selectFirst(".ll-update")?.text(),
            )
        }
    }

    private fun parseRelativeDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val amount = relativeDateRegex.find(value)?.value?.toIntOrNull() ?: return 0L

        val duration = when {
            "giây" in value -> amount.seconds
            "phút" in value -> amount.minutes
            "giờ" in value -> amount.hours
            "ngày" in value -> amount.days
            "tuần" in value -> (amount * 7).days
            "tháng" in value -> (amount * 30).days
            "năm" in value -> (amount * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()

        document.extractJson<ChapterDetailDto>("window.chapterDetail")
            ?.pages
            ?.takeIf { it.isNotEmpty() }
            ?.let { pages ->
                // First entry is a site watermark
                return pages.sortedBy { it.index }
                    .filterNot { watermarkImageRegex.containsMatchIn(it.url) }
                    .mapIndexed { i, p -> Page(i, imageUrl = p.url) }
            }

        return document.select("img.reading-img").mapNotNull { img ->
            imageUrl(img)?.takeUnless { watermarkImageRegex.containsMatchIn(it) }
        }.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/tim-kiem", Fetch.DOCUMENT).asJsoup()

        val categories = document.select(".list-cats .form-check input[value]")
            .mapNotNull { input ->
                val id = input.attr("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = document.selectFirst("label[for=${input.id()}]")?.text()
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CategoryOption(name = name, id = id)
            }

        val genres = document.select(".list-genres .tag-item[data-value]")
            .mapNotNull { element ->
                val id = element.attr("data-value").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = element.text().removePrefix("#").trim()
                    .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                GenreOption(name = name, id = id)
            }
            .distinctBy { it.id }

        return FilterData(genres = genres, categories = categories).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data)

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return document.select("#cm-related li, #cm-related .m-post")
            .mapNotNull { it.toSManga() }
            .filter { it.url != manga.url }
            .distinctBy { it.url }
    }

    // ============================= Utilities ==============================

    private fun imageUrl(element: Element): String? = element.attrOrNull("data-src")
        ?: element.attrOrNull("data-original")
        ?: element.attrOrNull("src")
            ?.takeUnless { it.contains("pre-load") || it.startsWith("data:") }

    private inline fun <reified T> Document.extractJson(variable: String): T? {
        val script = select("script:not([src])")
            .firstOrNull { it.data().contains("$variable = {") }
            ?.data()
            ?: return null

        val start = script.indexOf('{')
        if (start < 0) return null

        val json = when (val end = script.indexOf("};", start)) {
            -1 -> script.substring(start).trim().removeSuffix(";")
            else -> script.substring(start, end + 1)
        }

        return runCatching { json.parseAs<T>() }.getOrNull()
    }

    private val relativeDateRegex = Regex("""\d+""")
    private val watermarkImageRegex = Regex("""/[^/]*truyen[^/]*\.webp?$""", RegexOption.IGNORE_CASE)
}
