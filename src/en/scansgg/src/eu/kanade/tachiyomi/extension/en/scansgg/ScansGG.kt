package eu.kanade.tachiyomi.extension.en.scansgg

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ScansGG : HttpSource() {

    override val name = "ScansGG"
    override val baseUrl = "https://scans.gg"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.scans.gg"
    private val cdnUrl = "https://cdn.scans.gg/uploads"

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("limit", POPULAR_LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * POPULAR_LIMIT).toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResponseDto<List<SeriesDto>>>()
        val mangas = dto.data.map { it.toSManga(cdnUrl) }

        // The /series endpoint doesn't return the meta pagination object,
        // so we check if the returned items match our limit
        return MangasPage(mangas, mangas.size == POPULAR_LIMIT)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", LATEST_LIMIT.toString())
            .addQueryParameter("chapters", "true")
            .addQueryParameter("series_details", "true")
            .addQueryParameter("group_details", "true")
            .addQueryParameter("sort", "date")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResponseDto<List<SeriesDto>>>()
        val mangas = dto.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, dto.meta?.hasMore == true)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", POPULAR_LIMIT.toString())
            addQueryParameter("offset", ((page - 1) * POPULAR_LIMIT).toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            val types = filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
            val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
            val tags = filters.firstInstanceOrNull<TagFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()

            addQueryParameter("q_type", types.joinToString(",", "[", "]"))
            addQueryParameter("q_status", statuses.joinToString(",", "[", "]"))
            addQueryParameter("q_tags", tags.joinToString(",", "[", "]"))
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .addQueryParameter("trackers", "true")
            .addQueryParameter("sources", "true")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<ResponseDto<SeriesDto>>()
        return dto.data.toSManga(cdnUrl, tagsMap)
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        // Parse the series_id that we constructed in Dto.kt to safely open the correct webview
        val url = (apiUrl + chapter.url).toHttpUrl()
        val seriesId = url.queryParameter("series_id") ?: return baseUrl
        return "$baseUrl/series/$seriesId"
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", manga.url)
                .addQueryParameter("limit", CHAPTER_LIMIT.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("group_details", "true")
                .build()
            val response = client.newCall(GET(url, headers)).execute()
            val dto = response.parseAs<ResponseDto<List<ChapterDto>>>()

            chapters += dto.data.map { it.toSChapter(manga.url, dateFormat) }
            hasMore = dto.meta?.hasMore == true
            page++
        }
        chapters
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ResponseDto<PageListDto>>()
        return dto.data.toPages(cdnUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        TagFilter(),
    )

    companion object {
        private const val POPULAR_LIMIT = 21
        private const val LATEST_LIMIT = 14
        private const val CHAPTER_LIMIT = 100
    }
}
