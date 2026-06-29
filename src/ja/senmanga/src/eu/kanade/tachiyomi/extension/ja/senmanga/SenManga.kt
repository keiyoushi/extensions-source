package eu.kanade.tachiyomi.extension.ja.senmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SenManga : HttpSource() {
    override val name = "Sen Manga"
    override val baseUrl = "https://raw.senmanga.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/directory?order=Popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<DirectoryResponse>()
        val mangas = data.series.map { it.toSManga() }
        val hasNext = (data.currentPage ?: 1) < (data.totalPages ?: 1)

        return MangasPage(mangas, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/home?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<HomeResponse>()
        val mangas = data.series.map { it.toSManga() }

        // The home endpoint doesn't return total pages, but generally has 20 items per page
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/directory".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        }

        filters.firstInstanceOrNull<TypeFilter>()?.let {
            url.addQueryParameter("type", it.toUriPart())
        }

        filters.firstInstanceOrNull<StatusFilter>()?.let {
            url.addQueryParameter("status", it.toUriPart())
        }

        filters.firstInstanceOrNull<OrderFilter>()?.let {
            url.addQueryParameter("order", it.toUriPart())
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        OrderFilter(),
    )

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDto>().toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<SeriesDto>()
        val mangaSlug = data.slug

        return data.chapterList?.map { chapter ->
            SChapter.create().apply {
                url = "$mangaSlug/${chapter.url}"
                name = chapter.title
                date_upload = dateFormat.tryParse(chapter.datetime)
            }
        } ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/read/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ReadResponse>()
        return data.pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.url.substringBefore("/")
        val chapterSlug = chapter.url.substringAfter("/")
        return "$baseUrl/manga/$mangaSlug/chapter-$chapterSlug/"
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
