package eu.kanade.tachiyomi.extension.vi.sangchanhteam

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class SangChanhTeam : KeiSource() {

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = request.url.fragment
            ?.toHttpUrlOrNull()
            ?: return@Interceptor response

        if (response.code != 401 && response.code != 404) {
            return@Interceptor response
        }

        response.close()
        chain.proceed(GET(fallbackUrl, request.headers))
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(thumbnailFallbackInterceptor)
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getFilteredManga(page, sort = "views")

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getFilteredManga(page, sort = "updated")

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            if (page > 1) return MangasPage(emptyList(), false)

            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query.trim())
                .build()
            val mangas = client.get(url).parseAs<List<SearchDto>>()
                .filter { it.postType == null || it.postType == "manga" }
                .mapNotNull(::mangaFromSearchDto)
                .distinctBy { it.url }
            return MangasPage(mangas, false)
        }

        val url = filterUrl(page, filters)
        return parseMangaPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val pathSegments = url.pathSegments.filter(String::isNotEmpty)
        val isMangaUrl = pathSegments.size == 2 && pathSegments[0] == "truyen"
        val isChapterUrl = pathSegments.size == 3 &&
            pathSegments[0] == "truyen" &&
            pathSegments[2].startsWith("chap-")
        if (!isMangaUrl && !isChapterUrl) return null

        val detailUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("truyen")
            .addPathSegment(pathSegments[1])
            .addPathSegment("")
            .build()

        val manga = SManga.create().apply { setUrlWithoutDomain(detailUrl.toString()) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true).manga
    }

    private suspend fun getFilteredManga(page: Int, sort: String): MangasPage {
        val url = filterUrl(page, FilterList(), sort)
        return parseMangaPage(client.get(url))
    }

    private fun filterUrl(page: Int, filters: FilterList, defaultSort: String = "updated"): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("bo-loc-nang-cao")
        if (page > 1) {
            addPathSegment("page")
            addPathSegment(page.toString())
        }
        addPathSegment("")

        addQueryParameter("type", filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty())
        addQueryParameter("status", filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty())
        addQueryParameter("age_rating", filters.firstInstanceOrNull<AgeRatingFilter>()?.toUriPart().orEmpty())
        filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.forEach { addQueryParameter("genre[]", it.slug) }
        addQueryParameter("team", "")
        addQueryParameter("rating_min", "0")
        addQueryParameter("rating_max", "6")
        addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: defaultSort)
    }.build()

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaCardSelector)
            .map(::mangaFromElement)
            .distinctBy { it.url }
        val hasNextPage = document.selectFirst(".uk-pagination li:not(.uk-disabled) > a[aria-label='Trang sau']") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("h2 a[href*='/truyen/'], h3 a[href*='/truyen/'], a.uk-link-toggle[href*='/truyen/']")
            ?: error("Manga link not found")
        title = linkElement.text()
        setUrlWithoutDomain(linkElement.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.extractImageUrl()?.fullImageUrl()
    }

    private fun mangaFromSearchDto(dto: SearchDto): SManga? {
        val url = dto.url.takeIf { it.isNotBlank() } ?: return null
        return SManga.create().apply {
            title = Jsoup.parse(dto.title).text()
            setUrlWithoutDomain(url)
            thumbnail_url = dto.thumb?.fullImageUrl()
        }
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = if (fetchChapters) fetchChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("main h1")!!.text()
        author = null
        thumbnail_url = document.selectFirst("img[alt^='Ảnh bìa của']")?.extractImageUrl()
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        genre = document.select(".manga-block a[href*='/the-loai/']")
            .joinToString { it.text() }
            .ifEmpty { null }
        status = parseStatus(document.selectFirst("#manga-status")?.text())
        description = document.selectFirst("#manga-description")
            ?.wholeText()
            ?.trim()
            ?.ifEmpty { null }
    }

    private fun parseStatus(value: String?): Int {
        val status = value?.lowercase(Locale.ROOT) ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status || "đã theo kịp" in status -> SManga.ONGOING
            "trọn bộ" in status || "hoàn thành" in status -> SManga.COMPLETED
            "kết thúc mùa" in status || "tạm ngưng" in status -> SManga.ON_HIATUS
            "bị hủy" in status -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(firstDocument: Document): List<SChapter> {
        val lastPage = firstDocument.select(".uk-pagination a[href*='/chap/page/']")
            .mapNotNull { element ->
                val segments = element.absUrl("href").toHttpUrl().pathSegments
                val pageIndex = segments.indexOf("page")
                segments.getOrNull(pageIndex + 1)?.takeIf { pageIndex >= 0 }?.toIntOrNull()
            }
            .maxOrNull()
            ?: 1

        val remainingDocuments = coroutineScope {
            (2..lastPage).map { page ->
                async {
                    val url = firstDocument.location().toHttpUrl().newBuilder()
                        .addPathSegment("chap")
                        .addPathSegment("page")
                        .addPathSegment(page.toString())
                        .addPathSegment("")
                        .fragment("chapter-list")
                        .build()
                    client.get(url).asJsoup()
                }
            }.awaitAll()
        }

        return (listOf(firstDocument) + remainingDocuments)
            .flatMap(::chaptersFromDocument)
            .distinctBy { it.url }
    }

    private fun chaptersFromDocument(document: Document): List<SChapter> = document
        .select("#chapter-list a.uk-link-toggle[href*='/chap-']")
        .map { element ->
            val link = element.absUrl("href")
            val chapterSlug = link.toHttpUrl().pathSegments.last(String::isNotEmpty)
            SChapter.create().apply {
                setUrlWithoutDomain(link)
                name = element.selectFirst("h3")?.text()
                    ?.substringAfterLast("–")
                    ?.trim()
                    ?.replaceFirst("Chap", "Chương")
                    ?: chapterSlug.replace('-', ' ').replaceFirstChar(Char::uppercase)
                chapter_number = chapterNumberRegex.find(chapterSlug)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
                val time = element.selectFirst("time")
                date_upload = parseChapterDate(time?.attr("datetime"), time?.text())
            }
        }

    private fun parseChapterDate(datetime: String?, relativeText: String?): Long {
        if (!datetime.isNullOrBlank()) {
            return Instant.parseOrNull(datetime)?.toEpochMilliseconds() ?: 0L
        }

        return parseRelativeDate(relativeText)
    }

    private fun parseRelativeDate(value: String?): Long {
        val text = value?.lowercase(Locale.ROOT)?.trim() ?: return 0L
        if (text == "mới" || "vừa xong" in text) return Clock.System.now().toEpochMilliseconds()

        val amount = relativeDateNumberRegex.find(text)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in text -> amount.seconds
            "phút" in text -> amount.minutes
            "giờ" in text -> amount.hours
            "ngày" in text -> amount.days
            "tuần" in text -> (amount * 7).days
            "tháng" in text -> (amount * 30).days
            "năm" in text -> (amount * 365).days
            else -> return 0L
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapter-content img[data-imc-order]")
            .mapNotNull { image ->
                image.absUrl("data-original-src")
                    .ifEmpty { image.absUrl("src") }
                    .takeIf { it.isNotBlank() && !it.startsWith("data:") }
            }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/bo-loc-nang-cao/").asJsoup()
        .select("input.genre-checkbox[value]")
        .mapNotNull { element ->
            val name = element.attr("data-genre-name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = element.attr("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GenreOption(name, slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select(".manga-item-slider")
        .mapNotNull { element -> runCatching { mangaFromElement(element) }.getOrNull() }
        .distinctBy { it.url }

    // ============================= Utilities =============================

    private fun Element.extractImageUrl(): String? = absUrl("data-original-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("data-lazy-src") }
        .ifEmpty { absUrl("src") }
        .ifEmpty { null }

    private fun String.fullImageUrl(): String {
        val originalUrl = this
        val fullSizeUrl = replace(thumbnailSizeRegex, "$1")
        if (fullSizeUrl == originalUrl) return originalUrl

        return fullSizeUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment(originalUrl)
            ?.build()
            ?.toString()
            ?: originalUrl
    }

    private val mangaCardSelector = "main .uk-grid-small:has(> .uk-width-1-3):has(h2 a[href*='/truyen/'])"
    private val chapterNumberRegex = Regex("""^chap-(\d+(?:\.\d+)?)$""")
    private val relativeDateNumberRegex = Regex("""\d+""")
    private val thumbnailSizeRegex = Regex("""-\d+x\d+(\.[a-zA-Z0-9]+(?:\?.*)?)$""")
}
