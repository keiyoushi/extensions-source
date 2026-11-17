package eu.kanade.tachiyomi.multisrc.duckscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.text.Collator
import java.util.Locale
import kotlin.time.Clock

abstract class DuckScans : KeiSource() {

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ReaderInterceptor())

    // The site loads its whole catalogue at once and sorts and filters it itself
    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(fetchSeries().sortedByDescending { it.views })

    // Same order as the site's latest updates page
    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesPage(fetchSeries().sortedByDescending { it.lastChapterAt })

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val term = query.trim().lowercase()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.name }
        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: Filter.Sort.Selection(0, false)

        val comparator: Comparator<SeriesDto> = when (sort.index) {
            1 -> compareBy { it.likes }
            2 -> compareBy { it.releaseDate.orEmpty() }
            3 -> compareBy(Collator.getInstance(Locale.forLanguageTag("tr"))) { it.title }
            else -> compareBy { it.views }
        }
        val series = fetchSeries()
            .filter { term.isEmpty() || it.matches(term) }
            .filter { status == null || it.status == status }
            .filter { type == null || it.type == type }
            .filter { genres.isEmpty() || genres.any(it.genre::contains) }
            .sortedWith(if (sort.ascending) comparator else comparator.reversed())
        return seriesPage(series)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "seri" || segments[1].isEmpty()) return null

        val manga = SManga.create().apply { this.url = "/seri/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringAfterLast('/')
        val from = Fetch.XHR.from(getMangaUrl(manga))
        val details = if (fetchDetails) {
            async {
                val url = apiUrl.newBuilder().addPathSegment("series").addPathSegment(slug).build()
                client.get(url, from).parseAs<SeriesDetailsDto>().data.toSMangaDetails(baseUrl.toHttpUrl())
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async {
                val url = apiUrl.newBuilder()
                    .addPathSegment("chapters")
                    .addQueryParameter("series", slug)
                    .addQueryParameter("limit", "9999")
                    .build()
                val now = Clock.System.now().toEpochMilliseconds()
                client.get(url, from).parseAs<ChapterListDto>().data.map { it.toSChapter(now) }
            }
        } else {
            null
        }

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val (_, series, _, number) = chapterUrl.toHttpUrl().pathSegments
        val url = apiUrl.newBuilder()
            .addPathSegment("chapters")
            .addQueryParameter("series", series)
            .addQueryParameter("chapter", number)
            .addQueryParameter("includepages", "true")
            .addQueryParameter("limit", "1")
            .build()
        val data = client.get(url, Fetch.XHR.from(chapterUrl)).parseAs<ChapterListDto>().data.firstOrNull()
        if (data == null || data.locked) return emptyList()

        // The reader orders the files by the number at the end of their name
        return data.pages
            .sortedBy(::pageNumber)
            .mapIndexed { index, path -> Page(index, url = chapterUrl, imageUrl = baseUrl.toHttpUrl().resolve(path)!!.toString()) }
    }

    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers)
        .header("Accept", "image/webp,image/*;q=0.8")
        .fetch(Fetch.XHR.from(page.url))
        .tag(ReaderPage::class.java, ReaderPage(page.url))
        .build()

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder()
            .addPathSegments("admin/categories")
            .addQueryParameter("names", "true")
            .addQueryParameter("active", "true")
            .build()
        return client.get(url, Fetch.XHR.from("$baseUrl/series")).parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<CategoriesDto>()?.data)

    private suspend fun fetchSeries(): List<SeriesDto> {
        val url = apiUrl.newBuilder()
            .addPathSegment("series")
            .addQueryParameter("limit", "1000")
            .build()
        return client.get(url, Fetch.XHR.from("$baseUrl/series")).parseAs<SeriesListDto>().data
    }

    private fun seriesPage(series: List<SeriesDto>) = MangasPage(series.map { it.toSManga(baseUrl.toHttpUrl()) }, false)

    private val apiUrl: HttpUrl
        get() = baseUrl.toHttpUrl().newBuilder().addPathSegment("api").build()

    private fun pageNumber(path: String): Long {
        val name = URLDecoder.decode(path.substringBefore('?').substringAfterLast('/'), "UTF-8").substringBeforeLast('.')
        val number = TRAILING_NUMBER_REGEX.find(name)?.groupValues?.get(1) ?: NUMBER_REGEX.findAll(name).lastOrNull()?.value
        return number?.toLongOrNull() ?: 0L
    }

    companion object {
        private val TRAILING_NUMBER_REGEX = """(\d+)\s*$""".toRegex()
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
