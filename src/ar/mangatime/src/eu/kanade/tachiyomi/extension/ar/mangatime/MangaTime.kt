package eu.kanade.tachiyomi.extension.ar.mangatime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.tryParse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTime : HttpSource() {
    override val baseUrl = "https://mangatime.org"

    override val name = "MangaTime"

    override val lang = "ar"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    private val limit: Int = 24

    private fun String?.toStatus() = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String.toImage(): String {
        val t = this.replace(" ", "%20")
        return when {
            this.startsWith("http") -> t
            else -> "$baseUrl$t"
        }
    }

    private fun trpcUrl(endpoint: String, input: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("api/trpc/$endpoint")
        .addQueryParameter("batch", "1")
        .addQueryParameter("input", input)
        .build()

    private fun trackView(seriesId: String, chapterId: String) {
        client.newCall(
            POST(
                trpcUrl("content.trackView", "").toString(),
                headers,
                ViewQuery(seriesId, chapterId).trpcJson().toRequestBody("application/json".toMediaType()),
            ),
        ).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = response.closeQuietly()
            override fun onFailure(call: Call, e: IOException) {}
        })
    }
    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(trpcUrl("search.searchSeries", SearchDto(page, limit, "popularity", "desc").trpcJson()), headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseTrpcList<MangaListData>()

        val entries = result.results.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.coverUrl.toImage()
                url = "/${it.type}/${it.slug}#${it.id}"
            }
        }

        return MangasPage(entries, result.hasMore)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(trpcUrl("search.searchSeries", SearchDto(page, limit, "recent", "desc").trpcJson()), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(trpcUrl("search.searchSeries", SearchDto(page, limit, "popularity", "desc", query).trpcJson()), headers)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val seriesSlug = getMangaUrl(manga).toHttpUrl().pathSegments[1]
        return GET(trpcUrl("content.getSeriesBySlug", SeriesSlug(seriesSlug).trpcJson()), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val manga = response.parseTrpcList<SeriesDto>()

        title = manga.title
        thumbnail_url = manga.coverUrl.toImage()
        description = manga.description
        status = manga.status.toStatus()
        genre = ((manga.genres ?: emptyList()).map { it.name } + manga.type)
            .filter { it.isNotBlank() }.joinToString().replace("\u060c", ",") // Arabic comma
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val seriesId = manga.url.substringAfterLast("#")
        return GET(trpcUrl("content.getChapters", ChaptersQuery(seriesId, -1).trpcJson()), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseTrpcList<ChaptersDto>()

        return result.chapters.map {
            SChapter.create().apply {
                chapter_number = it.number.toFloat()
                name = it.title
                setUrlWithoutDomain("/chapter/${it.number}")
                date_upload = dateFormat.tryParse(it.publishedAt)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val seriesSlug = chapterUrl.pathSegments[1]
        val chapterNumber = chapterUrl.pathSegments[3].toInt()

        return GET(trpcUrl("content.getChapterPages", PagesQuery(seriesSlug, chapterNumber).trpcJson()), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseTrpcList<PagesDto>()

        if (!result.isUnlocked) throw Exception("Chapter is locked")

        trackView(result.seriesId, result.id)

        return result.pages.mapIndexed { i, image ->
            Page(i, imageUrl = image.toImage())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        chapter.url = manga.url.substringBefore("#") + chapter.url
    }
}
