package eu.kanade.tachiyomi.extension.es.mhscans

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MHScans : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 3.seconds) {
        it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/uploads/")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("views")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("updated")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val libraryUrl = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            filters.filterIsInstance<SelectFilter>().forEach { filter ->
                filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter(filter.param, it) }
            }
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(libraryUrl, Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("div.lib-grid > a[href^=/comics/]")
            // novel chapters are text without images, so they always end up as "No pages found"
            .filterNot { it.selectFirst("div.comic-cover ~ span")?.text() == "Novel" }
            .map { element ->
                SManga.create().apply {
                    url = element.absUrl("href").toHttpUrl().pathSegments[1]
                    title = element.selectFirst("p")!!.text()
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }
        val hasNextPage = document.selectFirst("nav.lib-pagination a[aria-label=Siguiente]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comics") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return fetchDetails(SManga.create().apply { this.url = slug })
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapters(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".comic-hero h1")!!.text()
            thumbnail_url = document.selectFirst(".comic-hero__cover img")?.absUrl("src")
            description = document.selectFirst("#comic-description")?.text()
            genre = document.select(".comic-chips li").joinToString { it.text() }
            status = when (document.selectFirst(".comic-meta__status")?.text()?.lowercase()) {
                "en emisión" -> SManga.ONGOING
                "completado" -> SManga.COMPLETED
                "en hiato" -> SManga.ON_HIATUS
                "cancelado", "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    private suspend fun fetchChapters(manga: SManga): List<SChapter> = coroutineScope {
        val fetch = Fetch.XHR.from(getMangaUrl(manga))
        val response = client.get("${getMangaUrl(manga)}/chapters?page=1", fetch)
        val totalPages = response.header("X-Pages")?.toIntOrNull() ?: 1
        val firstPage = parseChapters(response.asJsoup())

        firstPage + (2..totalPages).map { page ->
            async { parseChapters(client.get("${getMangaUrl(manga)}/chapters?page=$page", fetch).asJsoup()) }
        }.awaitAll().flatten()
    }

    // paid chapters link to /auth/login without a chapter id until they're bought
    private fun parseChapters(document: Document): List<SChapter> = document.select("a[href^=/leer/][data-chapter-num]").map { element ->
        SChapter.create().apply {
            url = element.absUrl("href").toHttpUrl().pathSegments[1]
            name = element.selectFirst("[data-cl-title]")!!.text()
            element.attr("data-chapter-num").toFloatOrNull()?.let { chapter_number = it }
            date_upload = element.selectFirst("[data-cl-title] + div > span")?.text().parseRelativeDate()
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/leer/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.page-img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.attrOrNull("abs:data-src") ?: img.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/library", Fetch.DOCUMENT).asJsoup()
        .select("a.dd-opt[href*=genre=]")
        .mapNotNull { it.absUrl("href").toHttpUrl().queryParameter("genre") }
        .distinct()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(TypeFilter())
            add(StatusFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
        },
    )

    private fun String?.parseRelativeDate(): Long {
        val (amount, unit) = this?.lowercase()?.let(RELATIVE_DATE::find)?.destructured ?: return 0L
        val chronoUnit = when {
            unit.startsWith("min") -> ChronoUnit.MINUTES
            unit.startsWith("h") -> ChronoUnit.HOURS
            unit.startsWith("d") -> ChronoUnit.DAYS
            unit.startsWith("sem") -> ChronoUnit.WEEKS
            unit.startsWith("mes") -> ChronoUnit.MONTHS
            unit.startsWith("a") -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(amount.toLong(), chronoUnit).toInstant().toEpochMilli()
    }

    companion object {
        private val RELATIVE_DATE = Regex("""hace (\d+) (\p{L}+)""")
    }
}
