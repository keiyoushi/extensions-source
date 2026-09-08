package eu.kanade.tachiyomi.extension.vi.zettruyen

import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ZetTruyen : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    private val apiDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    private fun DateTimeFormatter.tryParse(date: String?): Long = runCatching {
        LocalDateTime.parse(date, this)
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 0 }))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("name", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> setQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> setQueryParameter("status", filter.toUriPart())
                    is TypeFilter -> setQueryParameter("type", filter.toUriPart())
                    is ChapterFilter -> setQueryParameter("chapterRange", filter.toUriPart())
                    is GenreFilter -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.id }
                        setQueryParameter("genres", genres)
                    }
                    else -> {}
                }
            }
        }.build()

        return parseMangaPage(client.get(url).asJsoup())
    }

    private fun parseMangaPage(document: Document): MangasPage {
        val mangas = document.select("div.grid a[href*=/truyen-tranh/]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("span.line-clamp-2")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?) = getFilters()

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val isDetailUrl = url.pathSegments.size == 2
        val isChapterUrl = url.pathSegments.size == 3 && url.pathSegments[2].startsWith("chuong-")
        if (!isDetailUrl && !isChapterUrl) return null

        val mangaPath = "/truyen-tranh/$slug"
        return parseMangaDetails(client.get("$baseUrl$mangaPath").asJsoup()).apply {
            setUrlWithoutDomain(mangaPath)
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringAfterLast("/")

        val detailsDeferred = if (fetchDetails) {
            async {
                client.get(getMangaUrl(manga)).use { response ->
                    parseMangaDetails(response.asJsoup())
                }
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                val firstPageResponse = fetchChapterPage(slug, 1)
                val firstPageData = firstPageResponse.data ?: return@async emptyList()

                if (firstPageData.lastPage <= 1) {
                    return@async parseChapterList(firstPageData, slug)
                }

                val allChapters = parseChapterList(firstPageData, slug).toMutableList()

                val remainingChapters = (2..firstPageData.lastPage).map { page ->
                    async {
                        val response = fetchChapterPage(slug, page)
                        response.data?.let { parseChapterList(it, slug) } ?: emptyList()
                    }
                }.awaitAll().flatten()

                allChapters.addAll(remainingChapters)
                allChapters
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = detailsDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img[src*=/thumb/]")?.absUrl("src")
        author = document.getInfoValue("Tác giả")
        status = parseStatus(document.getInfoValue("Trạng thái"))
        genre = document.getGenres()
        description = document.selectFirst("p.comic-content")?.wholeText()?.trim()
    }

    private fun Document.getInfoValue(label: String): String? {
        val element = select("div, span, p").firstOrNull { it.ownText() == label }
            ?: return null
        return element.nextElementSibling()?.text()
    }

    private fun Document.getGenres(): String? {
        val genreLabel = select("div, span").firstOrNull {
            it.ownText() == "Thể loại" && it.closest("header") == null
        } ?: return null
        return genreLabel.nextElementSibling()
            ?.select("a")
            ?.joinToString { it.text() }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn Thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterPage(slug: String, page: Int): ChapterListResponse {
        val apiUrl = "$baseUrl/api/comics/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "100")
            .addQueryParameter("order", "desc")
            .build()
        val apiHeaders = headers.newBuilder()
            .add("Accept", "application/json")
            .build()

        return client.get(apiUrl, apiHeaders).parseAs<ChapterListResponse>()
    }

    private fun parseChapterList(data: ChapterData, slug: String): List<SChapter> = data.chapters.map { chapter ->
        val chapterSlug = chapter.chapterSlug.replace("chapter-", "chuong-")
        SChapter.create().apply {
            url = "/truyen-tranh/$slug/$chapterSlug"
            name = chapter.chapterName
            date_upload = chapter.updatedAt?.substringBefore(".")
                ?.let { apiDateFormat.tryParse(it) }
                ?: 0L
        }
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl${chapter.url}").use { response ->
        val document = response.asJsoup()
        document.select("div.center img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }.ifEmpty {
            document.select("div.w-full.mx-auto.center img").mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("src"))
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }
}
