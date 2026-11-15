package eu.kanade.tachiyomi.extension.vi.metruyen18sbs

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes

@Source
abstract class MeTruyen18 : KeiSource() {

    // The API allows 30 page lists and 60 chapter lists a minute
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ageGateInterceptor())
        .rateLimit(permits = 25, period = 1.minutes) { it.encodedPath.startsWith("/api/chapters/") }
        .rateLimit(permits = 50, period = 1.minutes) { it.encodedPath.startsWith("/api/comics/") }

    private val apiHeaders: Headers
        get() = headersBuilder().set("Accept", "application/json").build()

    // Until its age form is posted once, the site answers with that form, and its API with a 403
    private fun ageGateInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.host != baseUrl.toHttpUrl().host || request.url.encodedPath.startsWith("/cdn/")) return@Interceptor response
        val body = response.peekBody(PEEK_SIZE).string()
        if (AGE_FORM !in body && AGE_MESSAGE !in body) return@Interceptor response
        response.close()

        val home = request.newBuilder().url("$baseUrl/").removeHeader("Accept").get().fetch(Fetch.DOCUMENT).build()
        val token = chain.proceed(home).asJsoup().selectFirst("form[action$='$AGE_FORM'] input[name=_token]")!!.attr("value")
        val form = FormBody.Builder().add("_token", token).add("next", "/").add("confirm", "1").build()
        chain.proceed(request.newBuilder().url("$baseUrl$AGE_FORM").removeHeader("Accept").post(form).fetch(Fetch.DOCUMENT).build()).close()
        chain.proceed(request)
    }

    override suspend fun getPopularManga(page: Int) = getMangaList(listUrl(page).addQueryParameter("sort", "popular").build())

    override suspend fun getLatestUpdates(page: Int) = getMangaList(listUrl(page).addQueryParameter("sort", "updated").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl(page).apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.firstInstanceOrNull<GenreFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("genre", it) }
            filters.firstInstanceOrNull<FormatFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("format", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("status", it) }
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular")
            filters.firstInstanceOrNull<PeriodFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("period", it) }
        }.build()
        return getMangaList(url)
    }

    // The catalogue's own links (a genre, a sort) open as a list
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSiteUrl(url) || url.encodedPath != "/kham-pha") return super.getMangasByUrl(url, page)
        val listUrl = url.newBuilder().host(baseUrl.toHttpUrl().host).setQueryParameter("page", page.toString()).build()
        return getMangaList(listUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url) || url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/truyen/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun listUrl(page: Int) = "$baseUrl/kham-pha".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.comic-card").map { card ->
            val link = card.selectFirst("h3 a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("a.cover-link img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[aria-label=Trang sau]") != null)
    }

    // The page has the details and the dates of the 50 newest chapters, the API has every chapter
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val page = async {
            val url = getMangaUrl(manga).toHttpUrl().newBuilder().addQueryParameter("order", "desc").build()
            client.get(url, Fetch.DOCUMENT).asJsoup()
        }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else null }
        val document = page.await()
        val dates = document.select(".chapter-list a[data-chapter-id]").associate {
            it.attr("data-chapter-id") to parseRelativeDate(it.selectFirst("time")?.text())
        }
        SMangaUpdate(
            parseDetails(document).apply { url = manga.url },
            chapterList.await()?.map { it.toSChapter(dates[it.id.toString()] ?: 0L) } ?: chapters,
        )
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst(".detail-copy h1")!!.text()
        thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
        author = document.selectFirst(".detail-credits p:contains(Tác giả) strong")?.text()?.takeUnless { it in UNKNOWN_AUTHORS }
        genre = document.select(".genre-strip a").joinToString { it.text() }.ifEmpty { null }
        status = when (document.selectFirst(".status-pill")?.text()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            "Tạm ngưng" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(document.selectFirst(".detail-stats strong")?.text()?.toDoubleOrNull(), max = 5)
            .description(document.selectFirst(".detail-description")?.text())
            .altTitles(document.selectFirst(".detail-copy h1 + p.muted")?.text()?.split(',').orEmpty())
            .build()
    }

    private suspend fun fetchChapterList(manga: SManga): List<ChapterDto> {
        val slug = manga.url.substringAfterLast('/')
        val items = mutableListOf<ChapterDto>()
        var next: String? = "$baseUrl/api/comics/$slug/chapters?q="
        while (next != null) {
            val data = client.get(next, apiHeaders, Fetch.XHR.from(getMangaUrl(manga))).parseAs<ChapterListDto>()
            items += data.items
            next = data.next
        }
        return items.asReversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast('/')
        return client.get("$baseUrl/api/chapters/$id/images", apiHeaders, Fetch.XHR.from(getChapterUrl(chapter)))
            .parseAs<List<PageDto>>()
            .mapIndexed { index, page -> Page(index, imageUrl = page.url) }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/kham-pha", Fetch.DOCUMENT).asJsoup()
        .select("select[name=genre] option[value]")
        .filter { it.attr("value").isNotEmpty() }
        .map { GenreDto(it.text(), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreDto>>())

    private fun parseRelativeDate(text: String?): Long {
        val match = RELATIVE_DATE_REGEX.find(text ?: return 0L) ?: return 0L
        val unit = when (match.groupValues[2]) {
            "giây" -> ChronoUnit.SECONDS
            "phút" -> ChronoUnit.MINUTES
            "giờ" -> ChronoUnit.HOURS
            "ngày" -> ChronoUnit.DAYS
            "tuần" -> ChronoUnit.WEEKS
            "tháng" -> ChronoUnit.MONTHS
            else -> ChronoUnit.YEARS
        }
        return ZonedDateTime.now(ZoneOffset.UTC).minus(match.groupValues[1].toLong(), unit).toInstant().toEpochMilli()
    }

    companion object {
        private const val AGE_FORM = "/xac-nhan-tuoi"
        private const val AGE_MESSAGE = "Age confirmation required"
        private const val PEEK_SIZE = 16_384L
        private val UNKNOWN_AUTHORS = setOf("Đang cập nhật", "Updating")
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(giây|phút|giờ|ngày|tuần|tháng|năm)""")
    }
}
