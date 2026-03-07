package eu.kanade.tachiyomi.extension.ja.rawuwu

import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.ChapterPageResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.MangaDetailResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.RawUwUResponseDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class RawUwU : HttpSource() {

    override val name = "Raw UwU"
    override val baseUrl = "https://rawuwu.net"
    override val lang = "ja"
    override val supportsLatest = true

    private val dateFormat by lazy {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    }

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // --- BROWSE (POPULAR / LATEST / SEARCH) ---

    override fun getFilterList() = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        StatusFilter(),
        SortFilter(),
        GenreFilter(genres),
    )

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/spa/genre/all".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "most_viewed")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/spa/latest-manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addPathSegment("search").addQueryParameter("query", query)
        } else {
            filters.forEach { filter ->
                if (filter is UriFilter) {
                    filter.addToUri(url)
                } else if (filter is GenreFilter) {
                    val genreId = genres[filter.state].path
                    url.addPathSegment("genre")
                    url.addPathSegment(genreId)
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListResponse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListResponse(response)
    override fun searchMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    private fun parseMangaListResponse(response: Response): MangasPage {
        val result = response.parseAs<RawUwUResponseDto>()

        val mangas = result.manga_list?.map { manga ->
            SManga.create().apply {
                url = "/raw/${manga.manga_id}"
                title = manga.manga_name
                thumbnail_url = manga.manga_cover_img
            }
        } ?: emptyList()

        val hasNextPage = result.pagi?.button?.next?.let { it > 0 } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // --- DETAILS ---

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.split("/").last().filter { it.isDigit() }
        return GET("$baseUrl/spa/manga/$id")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailResponseDto>()

        return SManga.create().apply {
            val detail = result.detail ?: throw Exception("Could not find manga details")
            title = detail.manga_name
            thumbnail_url = detail.manga_cover_img_full
                ?: detail.manga_cover_img ?: ""

            val descriptionText = detail.manga_description ?: ""
            val altName = detail.manga_others_name ?: ""

            description = buildString {
                append(descriptionText)
                append("\n\n")

                if (altName.isNotEmpty()) {
                    append("Alternative Names: ")
                    append("\n• ")
                    append(altName.replace(",", "\n• "))
                }
            }

            author = result.authors?.joinToString { it.author_name }
            genre = result.tags?.joinToString { it.tag_name }

            val isActive = detail.manga_status
            status = when (isActive) {
                true -> SManga.COMPLETED
                false -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- CHAPTERS ---

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.split("/").last().filter { it.isDigit() }
        return GET("$baseUrl/spa/manga/$id", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseAs<MangaDetailResponseDto>()
        val mangaId = json.detail?.manga_id ?: ""
        val chaptersArray = json.chapters ?: return emptyList()

        return chaptersArray.map { chapter ->
            SChapter.create().apply {
                val num = chapter.chapter_number ?: ""
                url = "/read/$mangaId/chapter-$num"
                val title = chapter.chapter_title ?: ""
                name = if (title.isNotEmpty()) "Ch. $num - $title" else "Chapter $num"
                date_upload = parseDate(chapter.chapter_date_published ?: "")
            }
        }
    }

    // --- PAGES ---

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trim('/').split("/")
        val mangaId = segments[1]
        val chapterNum = segments[2].removePrefix("chapter-")

        return GET("$baseUrl/spa/manga/$mangaId/$chapterNum", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterPageResponseDto>()

        val chapterDetail = result.chapter_detail
        val serverUrl = chapterDetail?.server ?: ""
        val htmlContent = chapterDetail?.chapter_content ?: ""

        val document = Jsoup.parseBodyFragment(htmlContent)

        return document.select("img").mapIndexed { i, img ->
            val rawPath = img.attr("data-src")
                .ifEmpty { img.attr("src") }
                .removePrefix("/")

            Page(i, "", "$serverUrl/$rawPath")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return dateFormat.tryParse(dateStr)
    }
}
