package eu.kanade.tachiyomi.extension.vi.loppytoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get("$baseUrl/the-loai?type=1&sort=views&page=$page").asJsoup())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get("$baseUrl/the-loai?page=$page").asJsoup())

    private fun parseMangaPage(document: Document): MangasPage {
        val mangaList = document.select("div.story-result-item, div.comic-item").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[rel=next], a:contains(Next »), a[aria-label*=Next]") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a.story-result-title, a.story-result-cover, a") ?: return null
        val mangaUrl = linkElement.absUrl("href")
        if (mangaUrl.isNovelUrl()) return null

        return SManga.create().apply {
            setUrlWithoutDomain(mangaUrl)
            title = element.selectFirst("a.story-result-title, h3.comic-title")?.text()
                ?.takeIf(String::isNotEmpty)
                ?: element.selectFirst("img")?.attr("alt")?.takeIf(String::isNotEmpty)
                ?: return null
            thumbnail_url = element.selectFirst("a.story-result-cover img, .comic-cover img, img")?.absUrl("src")
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

        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "newest"
        val selectedGenreIds = filters.filterIsInstance<GenreGroup>()
            .flatMap { group ->
                group.state.filter { it.state }.map { it.id }
            }

        val url = "$baseUrl/the-loai".toHttpUrl().newBuilder()
            .addQueryParameter("type", "1")
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())

        if (filters.firstInstanceOrNull<ExcludeAdultFilter>()?.state == true) {
            url.addQueryParameter("exclude_adult", "1")
        }

        if (selectedGenreIds.isNotEmpty()) {
            url.addQueryParameter("genres", selectedGenreIds.joinToString(","))
        }

        return parseMangaPage(client.get(url.build()).asJsoup())
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
        title = document.selectFirst("div.info-title h2, h1.manga-title, div.info-title")!!.text()
        author = document.selectFirst(".info-row:has(.info-label:contains(Tác giả)) .info-value, span.meta-label:contains(Tác giả) + *")?.text()
        genre = document.select(".tags a[href*='/the-loai/'], a[href*='/the-loai/'], .manga-tags a.tag")
            .map { it.text().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
        thumbnail_url = document.selectFirst(".info-cover img.main-img, .info-cover img:not(.blur-bg), img.cover-image")
            ?.absUrl("src")?.normalizeThumbnailUrl()

        val altName = document.selectFirst("div.other-name p, div.other-name, span.meta-label:contains(Tên khác) + *")?.text()
        val descriptionElement = document.selectFirst("div.description, #desc, div.manga-description")
        val descriptionText = descriptionElement?.select("p")
            ?.map { it.text() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n\n")
            ?.ifEmpty { descriptionElement.text() }
            .orEmpty()
        description = if (!altName.isNullOrEmpty()) "Tên khác: $altName\n$descriptionText" else descriptionText

        status = document.selectFirst(".info-row:has(.info-label:contains(Tình trạng)) .info-value, span.meta-label:contains(Tình trạng) + *")
            ?.text()?.lowercase()?.let { statusText ->
                when {
                    "ongoing" in statusText || "dang-tien-hanh" in statusText -> SManga.ONGOING
                    "completed" in statusText || "hoan-thanh" in statusText -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(document: Document, manga: SManga): List<SChapter> {
        val chapters = parseChapters(document).toMutableList()
        val slug = baseUrl.toHttpUrl().resolve(manga.url)?.pathSegments?.getOrNull(1)
            ?: return chapters

        var offset = chapters.size
        var hasMore = document.selectFirst("button.load-more-btn, .load-more") != null

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

        return chapters.distinctBy { it.url }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("li.chapter-item, li.episode-item, a.chapter-item").mapNotNull { element ->
        val linkElement = if (element.tagName() == "a") element else element.selectFirst("div.episode-title a, a[href]") ?: return@mapNotNull null
        val chapterUrl = linkElement.absUrl("href").takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val chapterName = element.selectFirst("h3")?.text()?.takeIf(String::isNotBlank)
            ?: linkElement.ownText().takeIf(String::isNotBlank)
            ?: linkElement.text().takeIf(String::isNotBlank)
            ?: return@mapNotNull null

        SChapter.create().apply {
            setUrlWithoutDomain(chapterUrl)
            name = chapterName
            date_upload = element.selectFirst("span.chapter-date")?.text().toDate()
        }
    }

    private fun String?.toDate(): Long {
        val value = this?.trim() ?: return 0L
        val parsedDate = dateFormat.tryParseDate(value, vietnamZone)
        if (parsedDate != 0L) return parsedDate

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

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
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
        val document = client.get("$baseUrl/the-loai?type=1").asJsoup()
        val groups = document.select(".filter-table .frow").mapNotNull { row ->
            val label = row.selectFirst(".flabel")?.text()?.trim() ?: return@mapNotNull null
            val isPhanLoai = label.contains("Phân Loại", ignoreCase = true)

            val options = row.select(".fchips .gchip[data-id]").mapNotNull { chip ->
                val id = chip.attr("data-id").trim()
                val name = chip.text().trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                if (isPhanLoai && name.equals("Light Novel", ignoreCase = true)) return@mapNotNull null
                FilterOptionData(name, id)
            }
            if (options.isEmpty()) return@mapNotNull null
            FilterGroupData(label, options)
        }
        return groups.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val groups = data?.parseAs<List<FilterGroupData>>()
        return getFilters(groups)
    }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val heading = document.select("h2, h3")
            .firstOrNull { it.text().contains("Đề xuất", ignoreCase = true) }
            ?: return emptyList()
        val container = heading.parent()?.parent()?.parent() ?: return emptyList()

        return container.select(".comic-item, .story-result-item").mapNotNull(::mangaFromElement)
            .distinctBy { it.url }
    }
}
