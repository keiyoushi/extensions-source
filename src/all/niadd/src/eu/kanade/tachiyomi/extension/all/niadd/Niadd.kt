package eu.kanade.tachiyomi.extension.all.niadd

import android.util.LruCache
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Niadd : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::expiredImageInterceptor)
        .addNetworkInterceptor(::blockedUserInterceptor)

    // The site flags networks with a cookie and then serves a fake "404 Not Found" page with status 200 on every route.
    // A flagged network gets that page with or without the cookie, so the cookie only keeps blocking after a network change.
    private fun blockedUserInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = request.header("Cookie")
            ?.split("; ")
            ?.filterNot { it.startsWith("$BLOCKED_COOKIE=") }
            ?.joinToString("; ")
        val response = chain.proceed(
            request.newBuilder().apply {
                if (cookies.isNullOrEmpty()) removeHeader("Cookie") else header("Cookie", cookies)
            }.build(),
        )
        if (response.headers("Set-Cookie").any { it.startsWith("$BLOCKED_COOKIE=") }) {
            response.close()
            throw IOException(BLOCKED_NETWORK)
        }
        return response
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/list/Hot-Manga.html", Fetch.DOCUMENT).asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.manga-item").mapNotNull(::mangaFromElement)
        return MangasPage(mangas, document.selectFirst("a.next") != null)
    }

    // Lists carry blank cards linking /manga/.html
    private fun mangaFromElement(element: Element): SManga? {
        val title = element.selectFirst("div.manga-name")!!.textOrNull() ?: return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            thumbnail_url = element.selectFirst("div.manga-img img")?.absUrl("src")
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/list/New-Update.html", Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> addQueryParameter("author", filter.state)
                    is ArtistFilter -> addQueryParameter("artist", filter.state)
                    is SelectFilter -> addQueryParameter(filter.parameter, filter.value)
                    is GenreFilter -> {
                        addQueryParameter("category_id", filter.included.joinToString(","))
                        addQueryParameter("out_category_id", filter.excluded.joinToString(","))
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", "$page.html")
        }.build()
        return parseMangaList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") ||
            segments.size != 2 ||
            segments[0] !in ENTRY_PATHS ||
            !segments[1].endsWith(".html")
        ) {
            return null
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(url.newBuilder().query(null).fragment(null).build().toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) fetchDetails(manga) else manga },
        chapters = { if (fetchChapters) fetchChapterList(manga) else chapters },
    )

    private suspend fun fetchDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val info = document.selectFirst("div.bookside-bookinfo")
        val released = document.selectFirst("div.bookside-general-cell:has([itemprop=datePublished])")

        return SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.book-headline-name")!!.text()
            author = info?.select("a[href*=author=]")?.eachText()?.joinToString()?.ifEmpty { null }
            artist = info?.select("a[href*=artist=]")?.eachText()?.joinToString()?.ifEmpty { null }
            genre = document.select("div.bookside-general-cell [itemprop=genre]").eachText().joinToString().ifEmpty { null }
            status = when (document.selectFirst("span.book-status")?.text()?.lowercase()?.removeSurrounding("(", ")")) {
                in ONGOING_STATUSES -> SManga.ONGOING
                in COMPLETED_STATUSES -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = MarkdownDescription.Builder()
                .rating(
                    document.selectFirst("div.bookside-big-rate")?.text()?.toDoubleOrNull(),
                    max = 5,
                    votes = document.selectFirst("div.bookside-rate-vote span")?.text()?.toIntOrNull(),
                )
                .field(
                    released?.selectFirst("span")?.text()?.removeSuffix(":").orEmpty(),
                    released?.selectFirst("[itemprop=datePublished]")?.text(),
                )
                // The genre and uploader blocks share this class, and an empty genre block is blank
                .description(document.selectFirst("section.detail-synopsis:not(:has(a)):matchesOwn(\\S)"))
                // The unlabelled cell is the alternative names
                .altTitles(document.selectFirst("div.bookside-general-cell:not(:has(a, [itemprop]))")?.ownText()?.split(";").orEmpty())
                .build()
            thumbnail_url = document.selectFirst("div.bookside-img img")?.absUrl("src")
        }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("section.related-book-list div.bkslf-book-item")
        .map { element ->
            SManga.create().apply {
                title = element.selectFirst("div.bkslf-book-name")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val chaptersUrl = getMangaUrl(manga).removeSuffix(".html") + "/chapters.html"
        // Sent as built: to requests that look like a browser the site serves /go/rds/ ad redirects instead of chapter links
        val document = client.get(chaptersUrl, Fetch.NONE).asJsoup()
        document.selectFirst("ul.chapter-list")!!
        val isOriginal = manga.url.startsWith("/original/")

        return document.select("ul.chapter-list a.hover-underline[href*=/chapter/]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.chp-title")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst("span.chp-time")?.text())
                if (isOriginal) memo = buildJsonObject { put("original", true) }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val isOriginal = chapter.memo["original"]?.booleanOrNull == true
        return imageUrls(chapterUrl, isOriginal).mapIndexed { i, imageUrl ->
            // Chapter and index for expiredImageInterceptor
            val url = imageUrl.toHttpUrl().newBuilder().fragment("$chapterUrl|$isOriginal|$i").build()
            Page(i, imageUrl = url.toString())
        }
    }

    private suspend fun imageUrls(chapterUrl: String, isOriginal: Boolean): List<String> {
        val chapterHttpUrl = chapterUrl.toHttpUrl()
        val chapterId = chapterHttpUrl.pathSegments[2].removeSuffix(".html")
        val jumpUrl = readerJumpUrl(isOriginal)
        return try {
            if (jumpUrl == null) {
                siteReaderImageUrls(chapterHttpUrl, chapterId)
            } else {
                jumpReaderImageUrls(jumpUrl, chapterHttpUrl, chapterId)
            }
        } catch (e: Exception) {
            readerJumpMutex.withLock { jumpUrls.remove(isOriginal) }
            throw e
        }
    }

    // The site's reader shows at most 10 images per view
    private suspend fun siteReaderImageUrls(chapterUrl: HttpUrl, chapterId: String): List<String> = coroutineScope {
        fun viewUrl(view: Int) = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapter")
            .addPathSegment(chapterUrl.pathSegments[1])
            .addPathSegment("$chapterId-10-$view.html")
            .build()

        val firstView = client.get(viewUrl(1), Fetch.DOCUMENT.from(chapterUrl)).asJsoup()
        val otherViews = (2..firstView.select("option[value*=/$chapterId-10-]").size)
            .map { async { client.get(viewUrl(it), Fetch.DOCUMENT.from(chapterUrl)).asJsoup() } }
            .awaitAll()
        (listOf(firstView) + otherViews).flatMap { view ->
            view.select("img.manga_pic").map { it.absUrl("src") }.ifEmpty { throw Exception(PAGES_NOT_FOUND) }
        }
    }

    private suspend fun jumpReaderImageUrls(jumpUrl: HttpUrl, chapterUrl: HttpUrl, chapterId: String): List<String> {
        val url = jumpUrl.newBuilder().setQueryParameter("cid", chapterId).build()
        val html = client.get(url, Fetch.DOCUMENT.from(chapterUrl)).body.string()
        val imageList = ALL_IMGS_URL_REGEX.find(html)?.groupValues?.get(1) ?: throw Exception(PAGES_NOT_FOUND)
        return IMAGE_URL_REGEX.findAll(imageList).map { it.groupValues[1] }.toList()
    }

    private val refreshedImageUrls = LruCache<String, List<String>>(5)
    private val refreshMutex = Mutex()

    // Image urls are signed for about 18 hours and then answer 410, while the app keeps them in its page list cache
    private fun expiredImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val (chapterUrl, isOriginal, index) = request.url.fragment?.split('|')?.takeIf { it.size == 3 } ?: return chain.proceed(request)
        var url = request.url.newBuilder().fragment(null).build().toString()
        var response = chain.proceed(request)
        // A refreshed list from the cache can have expired too
        repeat(2) {
            if (response.code != 410) return response
            response.close()
            url = refreshedImageUrl(chapterUrl, isOriginal.toBoolean(), index.toInt(), url)
            response = chain.proceed(request.newBuilder().url(url).build())
        }
        return response
    }

    private fun refreshedImageUrl(chapterUrl: String, isOriginal: Boolean, index: Int, expiredUrl: String): String = try {
        // Reader pages sit on other hosts than the images, outside the image hosts' dispatcher slots
        runBlocking {
            refreshMutex.withLock {
                refreshedImageUrls.get(chapterUrl)?.takeIf { expiredUrl !in it }
                    ?: imageUrls(chapterUrl, isOriginal).also { refreshedImageUrls.put(chapterUrl, it) }
            }
        }.getOrNull(index) ?: throw IOException(PAGES_NOT_FOUND)
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e)
    }

    private val readerJumpMutex = Mutex()

    // Reader jump url for manga (false) and users' original works (true); null where the site links its own reader
    private val jumpUrls = HashMap<Boolean, HttpUrl?>()

    // Where the site's /chapter/ links redirect to a dead host, its pages link each chapter through /go/rds/
    // to a reader jump url that only differs in cid
    private suspend fun readerJumpUrl(isOriginal: Boolean): HttpUrl? = readerJumpMutex.withLock {
        if (jumpUrls.containsKey(isOriginal)) return@withLock jumpUrls[isOriginal]

        val listUrl = if (isOriginal) "$baseUrl/category/original/" else "$baseUrl/list/New-Update.html"
        val entryUrl = getMangaUrl(parseMangaList(client.get(listUrl, Fetch.DOCUMENT).asJsoup()).mangas.first())
        val redirectUrl = client.get(entryUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("a[href*=/go/rds/]")
            ?.absUrl("href")
        val jumpUrl = redirectUrl?.let {
            val script = client.get(it, Fetch.DOCUMENT.from(entryUrl)).body.string()
            JUMP_URL_REGEX.find(script)?.groupValues?.get(1)?.toHttpUrl() ?: throw Exception(PAGES_NOT_FOUND)
        }
        jumpUrl.also { jumpUrls[isOriginal] = it }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search/?type=high", Fetch.DOCUMENT).asJsoup()
        fun options(group: String) = document.select("[option_group_name=$group]").map { it.text() to it.attr("option_val") }

        return FilterData(
            genres = document.select("li.genre-item a[onclick*=clickGenre]").map {
                it.text() to GENRE_ID_REGEX.find(it.attr("onclick"))!!.groupValues[1]
            },
            years = options("publish_years"),
            ratings = options("rating"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return FilterList(
            listOfNotNull(
                AuthorFilter(),
                ArtistFilter(),
                SelectFilter("Completed", "completed_series", listOf("Either" to "", "Yes" to "YES", "No" to "NO")),
                filterData?.ratings?.takeIf { it.isNotEmpty() }?.let { SelectFilter("Rating", "rate_star", it) },
                filterData?.years?.takeIf { it.isNotEmpty() }?.let { SelectFilter("Released", "released", it) },
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            ),
        )
    }

    companion object {
        private const val PAGES_NOT_FOUND = "Chapter pages not found on the site"
        private const val BLOCKED_COOKIE = "niadd_bad_user_2"
        private const val BLOCKED_NETWORK = "Niadd has blocked this network, try another network"
        private val ENTRY_PATHS = setOf("manga", "original")
        private val GENRE_ID_REGEX = Regex("""'(\d+)'""")
        private val JUMP_URL_REGEX = Regex("""document\.location\s*=\s*'([^']+)'""")
        private val ALL_IMGS_URL_REGEX = Regex("""all_imgs_url\s*:\s*\[([\s\S]*?)\]""")
        private val IMAGE_URL_REGEX = Regex(""""(https?://[^"]+)"""")
        private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
        private val ONGOING_STATUSES = setOf("ongoing", "en marcha", "em progresso", "in corso", "laufend", "en cours", "постоянный")
        private val COMPLETED_STATUSES = setOf("completed", "terminado", "concluído", "completato", "abgeschlossen", "complété", "завершенный")
    }
}
