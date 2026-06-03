package eu.kanade.tachiyomi.extension.en.mangabay

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Mangabay : HttpSource() {

    override val name = "Manga-Bay"
    override val lang = "en"
    override val baseUrl = "https://read.manga-bay.org"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor(DleGuardResolver.interceptor(baseUrl))
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val fragment = request.url.fragment
            if (response.code == 404 && fragment != null && fragment.startsWith(FALLBACK_PREFIX)) {
                response.close()
                val fallbackUrl = fragment.removePrefix(FALLBACK_PREFIX)
                chain.proceed(
                    request.newBuilder()
                        .url(fallbackUrl)
                        .build(),
                )
            } else {
                response
            }
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!request.url.encodedPath.startsWith("/reader/")) {
                return@addNetworkInterceptor chain.proceed(request)
            }
            val mangaId = request.url.pathSegments.getOrNull(1).orEmpty()
            val existingCookies = request.header("Cookie")
                ?.split("; ")
                ?.filter { it.isNotEmpty() && !it.startsWith("adult=") }
                .orEmpty()
            val finalCookies = (existingCookies + "adult=$mangaId").joinToString("; ")
            chain.proceed(
                request.newBuilder()
                    .header("Cookie", finalCookies)
                    .build(),
            )
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(SortFilter.POPULAR_STATE)))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(SortFilter.LATEST_STATE)))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull()
        if (url != null && url.host == baseUrl.toHttpUrl().host) {
            if (!MANGA_PATH_REGEX.matches(url.encodedPath)) {
                throw Exception("Not a manga URL")
            }
            val manga = SManga.create().apply { setUrlWithoutDomain(url.encodedPath) }
            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it.apply { setUrlWithoutDomain(url.encodedPath) }), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(query.trim())
                .apply {
                    if (page > 1) {
                        addPathSegment("page")
                        addPathSegment(page.toString())
                        addPathSegment("")
                    }
                }
                .build()
            return GET(url, headers)
        }

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val filtersApplied = genreFilter?.state?.all { it.isIgnored() } == false

        if (filtersApplied) {
            urlBuilder.addPathSegment("ComicList")
            genreFilter.addToUrl(urlBuilder)
        } else {
            urlBuilder.addPathSegment("comix")
        }
        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
        }
        urlBuilder.addPathSegment("")
        val url = urlBuilder.build()

        val sort = filters.firstInstance<SortFilter>()
        if (sort.getSort().isEmpty()) {
            return GET(url, headers)
        }

        val form = FormBody.Builder()
            .add("dlenewssortby", sort.getSort())
            .add("dledirection", sort.getDirection())
            .apply {
                if (filtersApplied) {
                    add("set_new_sort", "dle_sort_xfilter")
                    add("set_direction_sort", "dle_direction_xfilter")
                } else {
                    add("set_new_sort", "dle_sort_cat_1")
                    add("set_direction_sort", "dle_direction_cat_1")
                }
            }
            .build()
        return POST(url.toString(), headers, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.pathSegments.firstOrNull() != "search") {
            parseFilters(document)
        }

        val entries = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText()
                }
                val miniUrl = element.selectFirst(".readed__img img")?.absUrl("data-src")
                val hdUrl = hdPosterUrl(url)
                thumbnail_url = when {
                    hdUrl == null -> miniUrl
                    miniUrl == null -> hdUrl
                    else -> "$hdUrl#$FALLBACK_PREFIX$miniUrl"
                }
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"
        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("article.page header.page__header h1")!!.text()
            thumbnail_url = document.selectFirst("div.page__poster img")?.absUrl("src")

            val altTitles = document.selectFirst("article.page header.page__header > h2")
                ?.text()
                ?.split(Regex("""\s*[;,/]\s*"""))
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val pageText = document.selectFirst("div.page__text")?.text()
            description = buildString {
                if (pageText != null) append(pageText)
                if (altTitles.isNotEmpty()) {
                    if (pageText != null) append("\n\n")
                    append("Alternative titles:")
                    altTitles.forEach { append("\n- ").append(it) }
                }
            }.ifEmpty { null }

            author = document.selectFirst(".page__list > li:has(> div:contains(Author))")?.ownText()
            artist = document.selectFirst(".page__list > li:has(> div:contains(Artist))")?.ownText()

            val type = document.select("div.page__meta-pills > span.page__meta-pill")
                .firstOrNull { !it.hasClass("page__meta-pill--status") }
                ?.text()
                ?.let {
                    when (it.lowercase()) {
                        "korean" -> "Manhwa"
                        "chinese" -> "Manhua"
                        "japanese" -> "Manga"
                        else -> it
                    }
                }
            val tagGenres = document.select("div.page__tags > a")
                .map { it.text().replaceFirstChar(Char::uppercaseChar) }
            genre = (listOfNotNull(type) + tagGenres).joinToString()

            status = when (document.selectFirst("span.page__meta-pill--status")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed", "finished" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = extractData(response)?.parseAs<ChapterListDto>()?.toSChapterList() ?: emptyList()

    override val disableRelatedMangasBySearch = true

    override fun relatedMangaListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val document = response.asJsoup()
        return document.select("section.sect--hot > .sect__content > a.poster").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".poster__title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = extractData(response)?.parseAs<PageListDto>() ?: return emptyList()
        return data.images.mapIndexed { idx, img ->
            val imageUrl = if (img.startsWith("http")) img.trim() else baseUrl + img.trim()
            Page(idx, imageUrl = imageUrl)
        }
    }

    private fun extractData(response: Response): String? {
        val script = response.asJsoup().selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: return null
        return script
            .substringAfter("window.__DATA__ = ")
            .substringBefore(";window.")
            .trim()
            .removeSuffix(";")
            .trim()
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder().apply {
            if (!page.imageUrl!!.contains("manga-bay.org")) {
                removeAll("Referer")
            }
        }.build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private val genresCacheFile: File by lazy {
        applicationContext.cacheDir.resolve("source_$id/genres.json")
    }
    private val genresLock = ReentrantLock()
    private var genres: List<FilterValue> = loadCachedGenres()
    private var filterParseFailed = false

    private fun loadCachedGenres(): List<FilterValue> = runCatching { genresCacheFile.readText().parseAs<List<FilterValue>>() }
        .getOrNull() ?: emptyList()

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Filters are ignored for text search"),
            SortFilter(),
        )
        if (genres.isNotEmpty()) {
            filters.add(GenreFilter(genres.map { it.value to it.id }))
        } else {
            filters.add(
                Filter.Header(
                    if (filterParseFailed) "Unable to load genres" else "Browse the catalogue once to load genres",
                ),
            )
        }
        return FilterList(filters)
    }

    private fun parseFilters(document: Document) {
        val script = document.selectFirst("script:containsData(window.__XFILTER__)")?.data() ?: run {
            filterParseFailed = true
            return
        }
        val parsed = try {
            script
                .substringAfter("window.__XFILTER__ = ")
                .substringBeforeLast(";")
                .trim()
                .parseAs<XFilters>()
                .genres
        } catch (e: Exception) {
            Log.e(name, "filter parse failed", e)
            filterParseFailed = true
            return
        }
        filterParseFailed = false
        if (parsed.map { it.id to it.value } == genres.map { it.id to it.value }) return

        genresLock.withLock {
            genres = parsed
            runCatching {
                genresCacheFile.parentFile?.mkdirs()
                genresCacheFile.writeText(parsed.toJsonString())
            }.onFailure { Log.e(name, "genre cache write failed", it) }
        }
    }

    private fun hdPosterUrl(mangaUrl: String): String? {
        val slug = MANGA_PATH_REGEX.matchEntire(mangaUrl)?.groupValues?.get(1) ?: return null
        val firstByte = MessageDigest.getInstance("MD5").digest(slug.toByteArray())[0].toInt() and 0xff
        val prefix = "%02x".format(firstByte)
        return "$baseUrl/uploads/posts/poster/$prefix/$slug.jpg"
    }

    companion object {
        private val MANGA_PATH_REGEX = Regex("""^/\d+-([^/]+)\.html$""")
        private const val FALLBACK_PREFIX = "fallback="
    }
}
