package eu.kanade.tachiyomi.extension.ru.astramanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class AstraManga : HttpSource() {

    override val supportsLatest = true

    private val domain by lazy { baseUrl.toHttpUrl().host }
    private val apiUrl by lazy { "https://api.$domain/api/v1" }
    private val mediaUrl by lazy { "https://$domain/media" }

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================

    private fun searchUrlBuilder(page: Int) = "$apiUrl/search".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("page_size", PAGE_SIZE.toString())

    override fun popularMangaRequest(page: Int): Request = GET(searchUrlBuilder(page).addQueryParameter("sort", "-popularity").build(), headers)

    override fun popularMangaParse(response: Response): MangasPage = searchParse(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET(searchUrlBuilder(page).addQueryParameter("sort", "-updated_at").build(), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = searchUrlBuilder(page)
        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query.trim())
        }
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> filter.selected.takeIf { it.isNotEmpty() }
                    ?.let { builder.addQueryParameter("type", it) }
                is StatusFilter -> filter.selected.takeIf { it.isNotEmpty() }
                    ?.let { builder.addQueryParameter("status", it) }
                is SortFilter -> if (query.isBlank()) {
                    builder.addQueryParameter("sort", filter.selected)
                }
                is GenreFilter -> filter.state.filter { it.state }
                    .forEach { builder.addQueryParameter("genres", it.id) }
                else -> {}
            }
        }
        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = searchParse(response)

    private fun searchParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>().data
        val mangas = data.titles.map { it.toSManga() }
        return MangasPage(mangas, data.currentPage < data.totalPages)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.slug()}", headers)

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<TitleDetailResponse>().data.toSMangaDetails()

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val slug = manga.slug()

        // Resolve the numeric title id (the titles endpoint accepts the slug).
        val titleId = client.newCall(GET("$apiUrl/titles/$slug", headers)).execute()
            .parseAs<TitleDetailResponse>().data.id

        val branches = client.newCall(GET("$apiUrl/titles/$titleId/branches", headers)).execute()
            .parseAs<BranchesResponse>().data.branches
        val branch = branches.firstOrNull { it.isMain == true }
            ?: branches.maxByOrNull { it.countChapters ?: 0 }
            ?: return@fromCallable emptyList<SChapter>()

        // branches include count_chapters, so fetch every chapter in one request.
        val pageSize = branch.countChapters?.takeIf { it > 0 } ?: CHAPTERS_PAGE_SIZE
        val url = "$apiUrl/branches/${branch.id}/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page_size", pageSize.toString())
            .build()
        client.newCall(GET(url, headers)).execute()
            .parseAs<ChaptersResponse>().data.items
            .map { it.toSChapter(slug) }
            .sortedByDescending { it.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/${chapter.chapterId()}/pages", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<PagesResponse>().data.pages
        // page_number is non-sequential (sliced webtoon images); rely on array order.
        return pages.mapIndexed { index, p -> Page(index, imageUrl = p.imageUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    // ============================== Mappers ===============================

    private fun SManga.slug(): String = url.substringAfter("/manga/").substringBefore("?").substringBefore("#")
    private fun SChapter.chapterId(): String = url.substringAfter("chapterId=").substringBefore("&").substringBefore("#")

    private fun TitleDto.coverUrl(): String? {
        val path = coverImage ?: coverVersions?.mid ?: coverVersions?.high ?: return null
        return "$mediaUrl/$path"
    }

    private fun TitleDto.toSManga(): SManga = SManga.create().apply {
        url = "/manga/$slug"
        title = name
        thumbnail_url = coverUrl()
    }

    private fun TitleDto.toSMangaDetails(): SManga = SManga.create().apply {
        url = "/manga/$slug"
        title = name
        thumbnail_url = coverUrl()
        author = publishingHouse?.name ?: publishers?.firstOrNull()?.name
        description = buildString {
            secondaryName?.takeIf { it.isNotBlank() }?.let { appendLine("Альт. название: $it") }
            alternativeNames?.takeIf { it.isNotEmpty() }
                ?.let { appendLine("Другие названия: ${it.joinToString()}") }
            year?.let { appendLine("Год выпуска: $it") }
            if (isNotEmpty()) appendLine()
            append(this@toSMangaDetails.description?.trim().orEmpty())
        }.trim()
        val tagNames = buildList {
            this@toSMangaDetails.type?.let { add(typeName(it)) }
            genres?.forEach { g -> g.name?.let { add(it) } }
            tags?.forEach { t -> t.name?.let { add(it) } }
        }
        genre = tagNames.filter { it.isNotBlank() }.distinct().joinToString()
        status = parseStatus(this@toSMangaDetails.status)
    }

    private fun ChapterDto.numberStr(): String = number.toString().removeSuffix(".0")

    private fun ChapterDto.toSChapter(slug: String): SChapter = SChapter.create().apply {
        url = "/manga/$slug/read/${numberStr()}?chapterId=$id"
        name = buildString {
            if (volumeNumber != null) append("Том $volumeNumber ")
            append("Глава ${numberStr()}")
            this@toSChapter.name?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
        }
        chapter_number = number
        date_upload = DATE_FORMAT.tryParse(publishedAt)
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "paused" -> SManga.ON_HIATUS
        "frozen", "discontinued" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun typeName(type: String): String = when (type) {
        "manga" -> "Манга"
        "manhwa" -> "Манхва"
        "manhua" -> "Маньхуа"
        "comics" -> "Комикс"
        else -> type
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val CHAPTERS_PAGE_SIZE = 10000

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
