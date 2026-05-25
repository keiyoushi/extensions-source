package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TaimuMangas : HttpSource() {

    override val name = "TaimuMangas"

    override val baseUrl = "https://taimumangas.rzword.xyz"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = seriesListRequest(
        page = page,
        sortBy = "total_views",
    )

    override fun popularMangaParse(response: Response): MangasPage = seriesListParse(response)

    override fun latestUpdatesRequest(page: Int): Request = seriesListRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = seriesListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return seriesListRequest(page, query.takeIf(String::isNotBlank), filters)
    }

    override fun searchMangaParse(response: Response): MangasPage = seriesListParse(response)

    override fun getFilterList(): FilterList = getFilters()

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$API_BASE_URL/library/series/${extractCode(manga.url)}/", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<SeriesDetailResponse>().series.toSManga()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { response ->
                val chapters = mutableListOf<ChapterSummary>()
                var chapterPage = response.parseAs<ChapterListResponse>().data

                chapters += chapterPage.chapters

                while (chapterPage.hasNext) {
                    chapterPage = client.newCall(chapterListRequest(manga, chapterPage.currentPage + 1))
                        .execute()
                        .use { it.parseAs<ChapterListResponse>().data }
                    chapters += chapterPage.chapters
                }

                chapters.map { it.toSChapter() }
            }
    }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga, 1)

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ChapterListResponse>().data.chapters.map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$API_BASE_URL/chapters/${extractCode(chapter.url)}/", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<ChapterDetailResponse>()
            .chapter
            .pages
            .sortedBy { it.number }
            .mapIndexed { index, page ->
                Page(index, imageUrl = mediaUrl(page.path))
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${extractCode(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${extractCode(chapter.url)}"

    private fun seriesListRequest(
        page: Int,
        query: String? = null,
        filters: FilterList = FilterList(),
        sortBy: String? = null,
    ): Request {
        val url = "$API_BASE_URL/library/series/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())

        if (!query.isNullOrBlank()) {
            url.addQueryParameter("name", query)
        }

        if (!sortBy.isNullOrBlank()) {
            url.addQueryParameter("sort_by", sortBy)
            url.addQueryParameter("sort_order", "desc")
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> filter.selectedValue().takeIf(String::isNotBlank)?.let {
                    url.addQueryParameter(filter.queryName, it)
                }
                is GenreFilter -> {
                    val includedGenres = filter.includedGenreIds()
                    val excludedGenres = filter.excludedGenreIds()

                    if (includedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres_include", includedGenres.joinToString(","))
                    }
                    if (excludedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres_exclude", excludedGenres.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = "$API_BASE_URL/library/series/${extractCode(manga.url)}/chapters/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", CHAPTER_PAGE_SIZE.toString())
            .build()

        return GET(url, headers)
    }

    private fun seriesListParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        return MangasPage(
            result.series.map { it.toSManga() },
            result.pagination.hasNext,
        )
    }

    private fun SeriesSummary.toSManga(): SManga = SManga.create().apply {
        url = code
        title = this@toSManga.title
        thumbnail_url = mediaUrl(cover)
        status = parseStatus(this@toSManga.status)
    }

    private fun SeriesDetail.toSManga(): SManga = SManga.create().apply {
        url = code
        title = this@toSManga.title
        thumbnail_url = mediaUrl(cover)
        status = parseStatus(this@toSManga.status)
        author = authors.takeIf(List<NameCode>::isNotEmpty)
            ?.joinToString { it.name }
            ?: this@toSManga.author?.name
        artist = artists.takeIf(List<NameCode>::isNotEmpty)
            ?.joinToString { it.name }
            ?: this@toSManga.artist?.name
        genre = genres.joinToString { it.name }
        description = buildString {
            synopsis?.takeIf(String::isNotBlank)?.let { append(it) }
            alternativeNames?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Nomes alternativos: $it")
            }
            group?.name?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Scanlator: $it")
            }
        }
    }

    private fun ChapterSummary.toSChapter(): SChapter = SChapter.create().apply {
        val number = numberText

        url = code
        chapter_number = number.toFloatOrNull() ?: -1f
        name = buildString {
            if (season > 1) {
                append("S")
                append(season)
                append(" - ")
            }
            append("Capitulo ")
            append(number)
            title?.takeIf(String::isNotBlank)?.let {
                append(" - ")
                append(it)
            }
        }
        date_upload = parseDate(createdAt)
    }

    private fun mediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null

        val cleanPath = path.trim().trimStart('/')
        return when {
            cleanPath.startsWith("http") -> cleanPath
            cleanPath.startsWith("media/") -> "$API_HOST/$cleanPath"
            else -> "$MEDIA_BASE_URL/$cleanPath"
        }
    }

    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase(Locale.ROOT)) {
            "ongoing", "em andamento" -> SManga.ONGOING
            "completed", "complete", "finalizado" -> SManga.COMPLETED
            "hiatus", "em hiato" -> SManga.ON_HIATUS
            "cancelled", "canceled", "cancelado", "dropped", "abandonada" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDate(date: String?): Long {
        if (date.isNullOrBlank()) return 0L

        val normalized = date.trim()
            .replace(MICROSECONDS_REGEX, ".$1")
            .replace(TIMEZONE_COLON_REGEX) { "${it.groupValues[1]}${it.groupValues[2]}" }

        for (format in DATE_FORMATS) {
            runCatching {
                synchronized(format) {
                    format.parse(normalized)?.time
                }
            }.getOrNull()?.let { return it }
        }

        return 0L
    }

    private fun extractCode(url: String): String = url.trimEnd('/').substringAfterLast('/')

    companion object {
        private const val API_HOST = "https://api.taimumangas.com"
        private const val API_BASE_URL = "$API_HOST/api/v2"
        private const val MEDIA_BASE_URL = "$API_HOST/media"
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100

        private val MICROSECONDS_REGEX = Regex("""\.(\d{3})\d+""")
        private val TIMEZONE_COLON_REGEX = Regex("""([+-]\d{2}):(\d{2})$""")

        private val UTC = TimeZone.getTimeZone("UTC")
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = UTC },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = UTC },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT),
        )
    }
}
