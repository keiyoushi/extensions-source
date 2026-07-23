package eu.kanade.tachiyomi.extension.vi.loppytoon

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LoppyToon : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangaList = document.select("div.hot-comic-item a.hot-comic-item").mapNotNull { element ->
            val mangaUrl = element.absUrl("href")
            if (mangaUrl.isNovelUrl()) return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaUrl)
                title = element.selectFirst("div.comic-title")?.text()
                    ?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?.normalizeThumbnailUrl()
            }
        }

        return MangasPage(mangaList, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get("$baseUrl/truyen-moi-cap-nhat?page=$page").asJsoup())

    private fun parseMangaPage(document: Document): MangasPage {
        val mangaList = document.select("div.comic-item").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("i.fa-chevron-right[onclick]") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a") ?: return null
        val mangaUrl = linkElement.absUrl("href")
        if (mangaUrl.isNovelUrl()) return null

        return SManga.create().apply {
            setUrlWithoutDomain(mangaUrl)
            title = element.selectFirst("h3.comic-title")?.text()
                ?.takeIf(String::isNotEmpty) ?: return null
            thumbnail_url = element.selectFirst(".comic-cover img")?.absUrl("src")
                ?.normalizeThumbnailUrl()
        }
    }

    private fun String.isNovelUrl(): Boolean {
        val url = toHttpUrlOrNull() ?: baseUrl.toHttpUrl().resolve(this) ?: return false
        val slug = url.pathSegments.getOrNull(1) ?: return false
        return url.pathSegments.firstOrNull() == "truyen" &&
            slug.split('-').any { it.equals("novel", ignoreCase = true) }
    }

    private fun String.normalizeThumbnailUrl(): String {
        val secondHttpsIndex = indexOf("https://", startIndex = "https://".length)
        return if (secondHttpsIndex != -1) substring(secondHttpsIndex) else this
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/api/search-story".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            val results = client.get(url).parseAs<List<SearchResult>>()
            val mangaList = results.mapNotNull { result ->
                val mangaUrl = "/truyen/${result.slug}"
                if (mangaUrl.isNovelUrl()) return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(mangaUrl)
                    title = result.title.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    thumbnail_url = result.cover?.let { cover ->
                        if (cover.startsWith("http")) cover else "$baseUrl/storage/$cover"
                    }?.normalizeThumbnailUrl()
                }
            }
            return MangasPage(mangaList, false)
        }

        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.firstOrNull { it.state }
            ?.let { selected ->
                return parseMangaPage(client.get("$baseUrl/the-loai/${selected.slug}?page=$page").asJsoup())
            }
        filters.firstInstanceOrNull<GroupFilter>()?.state
            ?.firstOrNull { it.state }
            ?.let { selected ->
                return parseMangaPage(client.get("$baseUrl/nhom-dich/${selected.slug}?page=$page").asJsoup())
            }

        return getLatestUpdates(page)
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        if (url.toString().isNovelUrl()) return null
        val manga = SManga.create().apply { setUrlWithoutDomain("/truyen/$slug") }
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
            chapters = if (fetchChapters) fetchChapterList(document, manga) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1.manga-title")!!.text()
        author = document.selectFirst("span.meta-label:contains(Tác giả)")?.nextElementSibling()?.text()
        genre = document.select(".manga-tags a.tag").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.cover-image")?.absUrl("src")?.normalizeThumbnailUrl()

        val altName = document.selectFirst("span.meta-label:contains(Tên khác)")?.nextElementSibling()?.text()
        val descriptionElement = document.selectFirst("div.manga-description")
        val descriptionText = descriptionElement?.select("p")
            ?.filter { it.text().isNotEmpty() }
            ?.joinToString("\n") { it.text() }
            ?.ifEmpty { descriptionElement.text() }
            .orEmpty()
        description = if (!altName.isNullOrEmpty()) "Tên khác: $altName\n$descriptionText" else descriptionText

        status = document.selectFirst("span.meta-label:contains(Tình trạng)")
            ?.nextElementSibling()?.text()?.lowercase()?.let { statusText ->
                when {
                    "ongoing" in statusText || "đang tiến hành" in statusText -> SManga.ONGOING
                    "completed" in statusText || "hoàn thành" in statusText -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(document: Document, manga: SManga): List<SChapter> {
        val slug = baseUrl.toHttpUrl().resolve(manga.url)?.pathSegments?.getOrNull(1)
            ?: return parseChapters(document)
        val chapters = parseChapters(document).toMutableList()
        var offset = chapters.size
        var hasMore = document.selectFirst("button.load-more-btn, .load-more") != null || chapters.size >= 20

        while (hasMore) {
            val url = "$baseUrl/load-more-chapters".toHttpUrl().newBuilder()
                .addQueryParameter("slug", slug)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("sortByPosition", "desc")
                .build()
            val chapterData = client.get(url).parseAs<ChapterResponse>()
            val newChapters = chapterData.html
                .takeIf(String::isNotBlank)
                ?.let { parseChapters(Jsoup.parseBodyFragment(it, baseUrl)) }
                .orEmpty()
            chapters += newChapters
            offset += newChapters.size
            hasMore = chapterData.hasMore && newChapters.isNotEmpty()
        }

        return chapters
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("a.chapter-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("h3")!!.text()
            date_upload = element.selectFirst("span.chapter-date")?.text().toDate()
        }
    }

    private fun String?.toDate(): Long {
        val value = this ?: return 0L
        val amount = relativeDateRegex.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L
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

    private val relativeDateRegex = Regex("""(\d+)""")

    // ================================ Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("img.manga-image")
        .mapIndexed { index, element ->
            val imageUrl = element.absUrl("src").ifEmpty { element.absUrl("data-src") }
            Page(index, imageUrl = imageUrl)
        }

    // =============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return FilterData(
            genres = document.parseFilterOptions("the-loai"),
            groups = document.parseFilterOptions("nhom-dich"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return getFilters(filterData)
    }

    private fun Document.parseFilterOptions(path: String): List<FilterOption> = select("nav .nav-dropdown a[href*='/$path/']")
        .mapNotNull { element ->
            val slug = element.absUrl("href").toHttpUrl().pathSegments.lastOrNull()
                ?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val name = element.text().removePrefix("»").trim()
                .takeIf(String::isNotEmpty) ?: return@mapNotNull null
            FilterOption(name, slug)
        }
        .distinctBy { it.slug }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val container = document.select("h2")
            .firstOrNull { it.text() == "Đề xuất liên quan" }
            ?.parent()
            ?.parent()
            ?: return emptyList()

        return container.select(".comic-grid .comic-item").mapNotNull(::mangaFromElement)
            .distinctBy { it.url }
    }
}
