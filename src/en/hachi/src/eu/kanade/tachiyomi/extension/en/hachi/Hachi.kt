package eu.kanade.tachiyomi.extension.en.hachi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Hachi : HttpSource() {
    override val baseUrl = "https://hachi.moe"
    override val lang = "en"
    override val name = "Hachi"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::buildIdOutdatedInterceptor)
        .build()

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code == 404 &&
            request.url.run {
                host == baseUrl.removePrefix("https://") &&
                    pathSegments.getOrNull(0) == "_next" &&
                    pathSegments.getOrNull(1) == "data" &&
                    fragment != "DO_NOT_RETRY"
            } &&
            response.header("Content-Type")?.contains("text/html") != false
        ) {
            // The 404 page should have the current buildId
            val document = response.asJsoup()
            buildId = fetchBuildId(document)

            // Redo request with new buildId
            val url = request.url.newBuilder()
                .setPathSegment(2, buildId)
                .fragment("DO_NOT_RETRY")
                .build()
            val newRequest = request.newBuilder()
                .url(url)
                .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    private val json: Json by injectLazy()
    private val apiBaseUrl = "https://api.${baseUrl.toHttpUrl().host}"

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBaseUrl/article".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "28")
            .addQueryParameter("property", "views")
            .addQueryParameter("direction", "desc")
            .addQueryParameter("query", "")
            .addQueryParameter("fields", "title")
            .addQueryParameter("tagMode", "false")
            .addQueryParameter("type", "")
            .addQueryParameter("status", "")
            .addQueryParameter("chapterCount", "4")
            .addQueryParameter("mature", "true")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiBaseUrl/article".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "28")
            .addQueryParameter("property", "latestChapterDate")
            .addQueryParameter("direction", "desc")
            .addQueryParameter("query", "")
            .addQueryParameter("fields", "title")
            .addQueryParameter("tagMode", "false")
            .addQueryParameter("type", "")
            .addQueryParameter("status", "")
            .addQueryParameter("chapterCount", "4")
            .addQueryParameter("mature", "true")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val request = mangaDetailsRequest(
            SManga.create().apply {
                url = "/article/${query.substringAfter(SEARCH_PREFIX)}"
            },
        )

        return client.newCall(request).asObservableSuccess().map { response ->
            val details = mangaDetailsParse(response)
            MangasPage(listOf(details), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiBaseUrl/article".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", "28")
            addQueryParameter("direction", "desc")
            addQueryParameter("query", query)
            addQueryParameter("fields", "title")
            addQueryParameter("tagMode", "false")
            addQueryParameter("type", "")
            addQueryParameter("status", "")
            addQueryParameter("chapterCount", "4")
            addQueryParameter("mature", "true")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ArticleResponseDto>()
        val mangas = dto.content.map { manga ->
            SManga.create().apply {
                setUrlWithoutDomain("/article/${manga.link}")
                title = manga.title
                artist = manga.artist
                author = manga.author
                description = manga.summary
                genre = manga.tags.joinToString()
                status = manga.status.parseStatus()
                thumbnail_url = manga.coverImage
                initialized = true
            }
        }

        return MangasPage(mangas, !dto.last)
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = patternMangaUrl.find(manga.url)?.groups?.get("slug")?.value
            ?: throw Exception("Failed to find manga from URL")

        val url = "$baseUrl/_next/data/$buildId/article/$slug.json".toHttpUrl().newBuilder()
            .addQueryParameter("url", slug)
            .build()

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        return super.mangaDetailsRequest(manga).url.toString()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<DetailsResponseDto>()

        return SManga.create().apply {
            url = "$baseUrl/article/${dto.pageProps.article.link}"
            title = dto.pageProps.article.title
            artist = dto.pageProps.article.artist
            author = dto.pageProps.article.author
            description = dto.pageProps.article.summary
            genre = dto.pageProps.article.tags.joinToString()
            status = dto.pageProps.article.status.parseStatus()
            thumbnail_url = dto.pageProps.article.coverImage
            initialized = true
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<DetailsResponseDto>()
        val chapters = dto.pageProps.chapters.map { chapter ->
            SChapter.create().apply {
                val chapterNumber = chapter.chapterNumber.toString().removeSuffix(".0")
                setUrlWithoutDomain("/article/${dto.pageProps.article.link}/chapter/$chapterNumber")
                name = "Chapter $chapterNumber"

                date_upload = runCatching {
                    dateFormat.parse(chapter.createdAt)?.time
                }.getOrNull() ?: 0
                chapter_number = chapter.chapterNumber
            }
        }

        return chapters
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val matchGroups = patternMangaUrl.find(chapter.url)!!.groups
        val slug = matchGroups["slug"]!!.value
        val number = matchGroups["number"]!!.value

        val url = "$baseUrl/_next/data/$buildId/article/$slug/chapter/$number.json".toHttpUrl()
            .newBuilder()
            .addQueryParameter("url", slug)
            .addQueryParameter("number", number)
            .build()

        return GET(url, headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return super.pageListRequest(chapter).url.toString()
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterResponseDto>()

        return dto.pageProps.images.mapIndexed { i, img ->
            Page(i, response.request.url.toString(), img)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Other
    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    private fun String.parseStatus() = when (this.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun fetchBuildId(document: Document? = null): String {
        val realDocument = document
            ?: client.newCall(GET(baseUrl, headers)).execute().use { it.asJsoup() }

        val nextData = realDocument.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Failed to find __NEXT_DATA__")

        val dto = json.decodeFromString<NextDataDto>(nextData)
        return dto.buildId
    }

    private var buildId = ""
        get() {
            if (field == "") {
                field = fetchBuildId()
            }
            return field
        }

    companion object {
        private val dateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

        private val patternMangaUrl =
            """/article/(?<slug>[^/]+)(?:/chapter/(?<number>[^/?&#]+))?""".toRegex()
        const val SEARCH_PREFIX = "slug:"
    }
}
