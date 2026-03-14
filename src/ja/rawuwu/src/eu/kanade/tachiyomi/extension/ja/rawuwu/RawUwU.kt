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
import java.util.TimeZone

class RawUwU : HttpSource() {

    override val name = "Raw UwU"
    override val baseUrl = "https://rawuwu.net"
    override val lang = "ja"
    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
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

        val mangas = result.mangaList?.map { manga ->
            SManga.create().apply {
                url = manga.mangaId.toString()
                title = manga.mangaName
                thumbnail_url = manga.mangaCoverImg
            }
        } ?: emptyList()

        val hasNextPage = result.pagi?.button?.next?.let { it > 0 } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // --- DETAILS ---

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/raw/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/spa/manga/${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailResponseDto>()

        return SManga.create().apply {
            val detail = result.detail ?: throw Exception("Could not find manga details")
            title = detail.mangaName
            thumbnail_url = detail.mangaCoverImgFull
                ?: detail.mangaCoverImg

            val descriptionText = detail.mangaDescription
            val altName = detail.mangaOthersName
            description = buildString {
                if (!descriptionText.isNullOrBlank()) append(descriptionText)

                if (!altName.isNullOrBlank()) {
                    if (isNotEmpty()) append("\n\n")

                    append("Alternative Names: ")
                    altName.split(",").forEach { name ->
                        append("\n - ${name.trim()}")
                    }
                }
            }

            author = result.authors?.joinToString { it.authorName }
            genre = result.tags?.joinToString { it.tagName }

            val isActive = detail.mangaStatus
            status = when (isActive) {
                true -> SManga.COMPLETED
                false -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- CHAPTERS ---

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/spa/manga/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailResponseDto>()
        val mangaId = result.detail?.mangaId ?: throw Exception("Could not find chapters")
        val chaptersArray = result.chapters ?: return emptyList()

        return chaptersArray.map { chapter ->
            SChapter.create().apply {
                val num = chapter.chapterNumber!!
                val formattedNum = if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
                url = "/read/$mangaId/chapter-$formattedNum"
                val title = chapter.chapterTitle?.trim()
                name = if (!title.isNullOrBlank()) "Ch. $formattedNum - $title" else "Chapter $formattedNum"
                date_upload = parseDate(chapter.chapterDatePublished ?: "")
            }
        }
    }

    // --- PAGES ---

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = baseUrl.toHttpUrl().resolve(chapter.url)!!.pathSegments
        val mangaId = segments[1]
        val chapterNum = segments[2].removePrefix("chapter-")

        return GET("$baseUrl/spa/manga/$mangaId/$chapterNum", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterPageResponseDto>()

        val chapterDetail = result.chapterDetail ?: throw Exception("Could not find chapter detail")
        val serverUrl = chapterDetail.server ?: throw Exception("Could not server url")
        val htmlContent = chapterDetail.chapterContent ?: throw Exception("Could not find chapter pages")

        val document = Jsoup.parseBodyFragment(htmlContent)

        return document.select("img").mapIndexed { i, img ->
            val rawPath = img.attr("data-src").removePrefix("/")

            Page(i, imageUrl = "$serverUrl/$rawPath")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
    private fun parseDate(dateStr: String?): Long = dateFormat.tryParse(dateStr)
}
