package eu.kanade.tachiyomi.extension.id.manhwaindo

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.ZonedDateTime

@Source
abstract class ManhwaIndo : KeiSource() {

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::headersInterceptor)
        rateLimit(4)
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter(0)))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter(1)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query.trim())
                .build()
        } else {
            "$baseUrl/series/page/$page/".toHttpUrl().newBuilder().apply {
                addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "views")
                filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("status", it)
                }
                filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("type", it)
                }
                filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach {
                    addQueryParameter("genre[]", it.value)
                }
            }.build()
        }

        val document = client.get(url).asJsoup()
        val mangas = document.select("a.series:has(img)").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        // the site emits duplicate class attributes on pagination links, so .next/.current can't be used
        val hasNextPage = document.selectFirst("nav.pagination li:has([aria-current=page]) + li a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val mangaUrl = "/series/$slug/"
        return parseMangaDetails(client.get(baseUrl + mangaUrl).asJsoup()).apply { this.url = mangaUrl }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = manga.memo.getStringOrNull("seriesId")

        return if (fetchChapters && seriesId != null) {
            coroutineScope {
                val chapterList = async { fetchChapterList(seriesId) }
                val details = async { if (fetchDetails) getMangaDetails(manga) else manga }
                SMangaUpdate(details.await(), chapterList.await())
            }
        } else {
            val details = getMangaDetails(manga)
            val chapterList = if (fetchChapters) fetchChapterList(details.memo.getString("seriesId")) else chapters
            SMangaUpdate(details, chapterList)
        }
    }

    private suspend fun getMangaDetails(manga: SManga) = parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup()).apply { url = manga.url }

    private suspend fun fetchChapterList(seriesId: String) = client.get("$baseUrl/wp-json/agc/v1/chapters?series=$seriesId").parseAs<List<ChapterDto>>().map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.url)
            name = "Chapter ${it.chapter}"
            date_upload = parseRelativeDate(it.time)
        }
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        description = document.selectFirst(".page-content p")?.text()
        author = document.infoValue("Penulis")
        artist = document.infoValue("Artist")
        val badges = document.select("span.badge").map { it.text() }
        genre = (document.select("a.btn-outline[href*=/genres/]").map { it.text() } + badges.take(1)).joinToString()
        status = when {
            badges.any { it.equals("Ongoing", true) } -> SManga.ONGOING
            badges.any { it.equals("Completed", true) } -> SManga.COMPLETED
            badges.any { it.equals("Hiatus", true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject {
            put("seriesId", document.select("script").firstNotNullOfOrNull { seriesIdRegex.find(it.data()) }!!.groupValues[1])
        }
    }

    private fun Document.infoValue(label: String) = selectFirst("div:has(> strong:containsOwn($label)) > span")?.text()

    // the api only gives wordpress' human_time_diff, e.g. "4 days lalu"
    private fun parseRelativeDate(time: String): Long {
        val (amount, unit) = relativeDateRegex.find(time.lowercase())?.destructured ?: return 0L
        val value = amount.toLong()
        val now = ZonedDateTime.now()
        val date = when {
            unit.startsWith("week") || unit.startsWith("minggu") -> now.minusWeeks(value)
            unit.startsWith("sec") || unit.startsWith("detik") -> now.minusSeconds(value)
            unit.startsWith("min") || unit.startsWith("menit") -> now.minusMinutes(value)
            unit.startsWith("hour") || unit.startsWith("jam") -> now.minusHours(value)
            unit.startsWith("day") || unit.startsWith("hari") -> now.minusDays(value)
            unit.startsWith("month") || unit.startsWith("bulan") -> now.minusMonths(value)
            unit.startsWith("year") || unit.startsWith("tahun") -> now.minusYears(value)
            else -> return 0L
        }
        return date.toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".reader-area img")
            .map { it.attr("abs:src") }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/series/").asJsoup()
        .select("input[name='genre[]'][value~=.+]")
        .map { GenreDto(it.parent()!!.text(), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filters are ignored when searching by title"),
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
        )
        data?.parseAs<List<GenreDto>>()?.let { filters.add(GenreFilter(it)) }
        return FilterList(filters)
    }

    private fun headersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val urlString = url.toString()
        val newHeaders = request.headers.newBuilder().apply {
            removeAll("X-Requested-With")
            if (urlString.contains("gmbr.pro") || urlString.contains("manhwaindo.my")) {
                set("Sec-Fetch-Site", if (urlString.contains("manhwaindo.my")) "same-origin" else "cross-site")
                set("Sec-Fetch-Mode", "no-cors")
                set("Sec-Fetch-Dest", "image")
            }
        }.build()

        val newRequest = if (url.scheme == "http" && (urlString.contains("gmbr.pro") || urlString.contains("manhwaindo.my"))) {
            request.newBuilder()
                .url(url.newBuilder().scheme("https").build())
                .headers(newHeaders)
                .build()
        } else {
            request.newBuilder()
                .headers(newHeaders)
                .build()
        }

        return chain.proceed(newRequest)
    }

    companion object {
        private val seriesIdRegex = Regex("""seriesId:\s*(\d+)""")
        private val relativeDateRegex = Regex("""(\d+)\s*([a-z]+)""")
    }
}
