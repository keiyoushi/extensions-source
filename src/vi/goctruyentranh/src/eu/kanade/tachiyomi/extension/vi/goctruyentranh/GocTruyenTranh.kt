package eu.kanade.tachiyomi.extension.vi.goctruyentranh

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class GocTruyenTranh : KeiSource() {

    private val searchUrl get() = "$baseUrl/baseapi/comics/filterComic"

    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.US)

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (response.code == 400 && request.url.encodedPath.contains("_next/image")) {
                val widths = listOf("384", "750", "960", "256", "1200", "1920", "3840", "96")
                for (width in widths) {
                    val newUrl = request.url.newBuilder().setQueryParameter("w", width).build()
                    val retryResponse = chain.proceed(request.newBuilder().url(newUrl).build())
                    if (retryResponse.isSuccessful) {
                        response.close()
                        return@addInterceptor retryResponse
                    }
                    retryResponse.close()
                }
            }
            response
        }
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach/truyen-hot?page=$page"

        return parseMangaPage(client.get(url))
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page"

        return parseMangaPage(client.get(url))
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("categories", it) }

                    is StatusList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("status", it) }

                    is ChapterCountList -> addQueryParameter("minChap", filter.values[filter.state].id)

                    is SortByList -> addQueryParameter("sort", filter.values[filter.state].id)

                    is CountryList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("country", it) }

                    else -> {}
                }
            }
        }.build()

        client.get(url, headers).use { response ->
            val json = response.parseAs<SearchDTO>()
            val manga = json.comics.data.map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = getThumbnail(it.thumbnail)
                    setUrlWithoutDomain("$baseUrl/" + it.slug)
                }
            }
            val hasNextPage = json.comics.current_page != json.comics.last_page
            return MangasPage(manga, hasNextPage)
        }
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("section.mt-12 > .grid > .flex").map { element ->
            SManga.create().apply {
                val sel = element.selectFirst("a.line-clamp-2")!!
                setUrlWithoutDomain(sel.absUrl("href"))
                title = sel.text()
                thumbnail_url = getImgUrl(element.selectFirst("img"))
            }
        }
        val hasNextPage = document.selectFirst("nav ul li") != null
        return MangasPage(manga, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host) {
            client.get(url, headers).use { response ->
                return parseMangaDetails(response.asJsoup())
            }
        }
        return null
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            parseMangaDetails(document),
            parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.select("section aside:first-child h1").text()
        genre = document.select("span:contains(Thể loại:) ~ a").joinToString { it.text().trim(',', ' ') }
        description = document.select("div.mt-3").joinToString("\n\n") { container ->
            val blocks = container.select("p")
            if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.wholeText().trim() } else container.wholeText().trim()
        }
        thumbnail_url = getImgUrl(document.selectFirst("section aside:first-child img"))
        status = parseStatus(document.selectFirst("span:contains(Trạng thái:) + b")?.text())
        author = document.selectFirst("span:contains(Tác giả:) + b")?.text()
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("section ul li a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".items-center:contains(Chapter)")!!.text()
            date_upload = parseDate(element.select(".text-center").text())
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        client.get(getChapterUrl(chapter), headers).use { response ->
            return response.asJsoup().select("img.lozad").mapIndexed { i, e ->
                Page(i, imageUrl = e.absUrl("data-src"))
            }
        }
    }

    private fun parseDate(date: String): Long = runCatching {
        val now = Clock.System.now()
        val number = date.replace(Regex("[^0-9]"), "").trim().toIntOrNull() ?: return 0L
        val duration = when (date.replace(Regex("[0-9]"), "").lowercase().trim()) {
            "giây trước" -> number.seconds
            "phút trước" -> number.minutes
            "giờ trước" -> number.hours
            "ngày trước" -> number.days
            else -> return dateFormat.tryParse(date)
        }
        (now - duration).toEpochMilliseconds()
    }.getOrNull() ?: 0L

    private fun DateTimeFormatter.tryParse(date: String): Long = runCatching {
        LocalDateTime.parse(date, this)
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    private fun getImgUrl(element: Element?): String? {
        val url = element?.absUrl("src")?.takeIf { it.isNotEmpty() }
            ?: element?.attr("srcset")?.takeIf { it.isNotEmpty() }?.let { srcset ->
                val firstUrl = srcset.split(',').first().trim().split(' ').first()
                if (firstUrl.startsWith("http")) firstUrl else "$baseUrl$firstUrl"
            }
        return getThumbnail(url)
    }

    private fun getThumbnail(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val httpUrl = url.toHttpUrlOrNull() ?: return url

        val builder = if (url.contains("_next/image")) {
            httpUrl.newBuilder()
        } else {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("_next")
                .addPathSegment("image")
                .addQueryParameter("url", url)
        }

        return builder
            .setQueryParameter("w", "384")
            .setQueryParameter("q", "75")
            .build().toString()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        GenreList(getGenreList()),
        StatusList(),
        ChapterCountList(),
        SortByList(),
        CountryList(),
    )
}
