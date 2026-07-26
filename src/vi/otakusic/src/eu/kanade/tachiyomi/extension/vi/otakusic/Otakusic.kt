package eu.kanade.tachiyomi.extension.vi.otakusic

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
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Otakusic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "application/json")
            .build()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = searchUrl(page)
            .addQueryParameter("sort", "views")
            .build()
        return parseMangaListPage(client.get(url).asJsoup())
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = searchUrl(page)
            .addQueryParameter("sort", "updated")
            .build()
        return parseMangaListPage(client.get(url).asJsoup())
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = searchUrl(page).apply {
            if (query.isNotEmpty()) addQueryParameter("q", query)

            filters.firstInstanceOrNull<StatusFilter>()
                ?.toUriPart()
                ?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<GenreFilter>()
                ?.toUriPart()
                ?.let { addQueryParameter("category", it) }
            addQueryParameter(
                "sort",
                filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "updated",
            )
        }.build()

        return parseMangaListPage(client.get(url).asJsoup())
    }

    private fun searchUrl(page: Int) = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaList = document.select("a[href*=/chi-tiet/]")
            .mapNotNull(::parseMangaCard)
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst("a.pagination-btn:contains(Sau)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseMangaCard(element: Element): SManga? {
        val image = element.selectFirst("img") ?: return null
        val title = image.attr("alt").ifEmpty { image.attr("title") }.ifEmpty { return null }

        return SManga.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            this.title = title
            thumbnail_url = image.absUrl("src")
        }
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val slug = when (url.pathSegments.firstOrNull()) {
            "chi-tiet" -> url.pathSegments.getOrNull(1)
            "doc-truyen" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain("/chi-tiet/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = if (fetchDetails) {
            async { parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup(), manga) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { loadChapterList(manga) }
        } else {
            null
        }

        SMangaUpdate(
            manga = mangaDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1")!!.text()
        author = document.select("h2:contains(Tác giả) + div a, h2:contains(Tác giả) ~ a")
            .joinToString { it.text() }
            .ifEmpty {
                document.selectFirst("h2:contains(Tác giả)")
                    ?.parent()
                    ?.ownText()
                    ?.replace(":", "")
                    ?.takeIf { it.isNotEmpty() && it != "Đang cập nhật" }
            }
        genre = document.select("div.flex.flex-wrap.gap-2 a")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst("#description")?.text()
        thumbnail_url = document.selectFirst("img[alt]")?.absUrl("src")
        status = when {
            document.selectFirst("a[href*='status=ongoing']") != null -> SManga.ONGOING
            document.selectFirst("a[href*='status=completed']") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private suspend fun loadChapterList(manga: SManga): List<SChapter> {
        val slug = getMangaUrl(manga).toHttpUrl().pathSegments.getOrNull(1) ?: return emptyList()
        val chapters = client.get("$baseUrl/api/v1/manga/chapters/$slug", apiHeaders)
            .parseAs<ChaptersResponse>().data

        return chapters
            .filter { it.status != "inactive" }
            .map { dto ->
                SChapter.create().apply {
                    setUrlWithoutDomain("$chapterUrlPrefix$slug/${dto.chapterOriginalSlug}/${dto.chapterSlug}")
                    name = "Chương ${dto.chapterName.content}"
                    date_upload = parseDate(dto.publicAt ?: dto.updatedAt)
                }
            }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapterPathSegments(chapter)
        return "$baseUrl/doc-truyen/${parts[0]}/${parts[2]}"
    }

    private fun parseDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDateTime.parse(date, dateFormat)
                .atZone(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun chapterPathSegments(chapter: SChapter) = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.drop(2)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapterPathSegments(chapter)
        val mangaSlug = parts[0]
        val chapterOriginalSlug = parts[1]
        val chapters = client.get("$baseUrl/api/v1/manga/chapters/$mangaSlug", apiHeaders)
            .parseAs<ChaptersResponse>().data
        val chapterDto = chapters.firstOrNull { it.chapterOriginalSlug == chapterOriginalSlug }
            ?: return emptyList()
        val apiUrl = chapterDto.apiUrl ?: return emptyList()

        return if (apiUrl.toHttpUrlOrNull() != null) {
            val pageData = client.get(apiUrl).parseAs<ChapterPagesResponse>().data
            pageData.item.chapterImages
                .sortedBy { it.page }
                .mapIndexed { index, image ->
                    val imageUrl = "${pageData.domainCdn}/${pageData.item.chapterPath}/${image.file}"
                    Page(index, imageUrl = imageUrl)
                }
        } else {
            apiUrl.parseAs<List<String>>().mapIndexed { index, filename ->
                val imageUrl = "$imgBaseUrl/manga/uploads/chapter/$mangaSlug/$chapterOriginalSlug/$filename"
                Page(index, imageUrl = imageUrl)
            }
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("input[name=category]")
        .mapNotNull { input ->
            val value = input.attr("value").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = input.parent()?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(name, value)
        }
        .distinctBy { it.value }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val heading = document.select("h2")
            .firstOrNull { it.text().equals("Có thể bạn sẽ thích", ignoreCase = true) }
            ?: return emptyList()
        val section = heading.parent()?.parent() ?: return emptyList()

        return section.select("a[href*=/chi-tiet/]")
            .mapNotNull(::parseMangaCard)
            .distinctBy { it.url }
    }

    private val imgBaseUrl get() = baseUrl.replace("://", "://img.")
    private val chapterUrlPrefix = "/api/chapter/"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
