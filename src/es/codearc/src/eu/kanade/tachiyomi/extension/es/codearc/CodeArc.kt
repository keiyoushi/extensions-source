package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class CodeArc : HttpSource() {

    override val name = "Code Arc Mangas"
    override val baseUrl = "https://mangas.codearctraducciones.com"
    override val lang = "es"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .rateLimitHost("https://cdn.codearctraducciones.com".toHttpUrl(), 1, 1) // 1 request per second for images
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ======================= Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking?mode=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Ranking page uses "a.group.relative.min-w-0" with title in "div.truncate.text-base"
        val mangas = document.select("a.group.relative.min-w-0[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.truncate.text-base")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?: element.selectFirst("img")?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = currentPage < POPULAR_MAX_PAGE && mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ======================= Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Latest page uses React Streaming SSR with links inside hidden <div id="S:1">
        // Jsoup parses hidden divs, so we can select the manga links directly.
        // These use "a.group" with class "overflow-hidden rounded-xl" and title in "div.line-clamp-2"
        val mangas = document.select("a.group.overflow-hidden[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.line-clamp-2")?.text()
                    ?: element.attr("aria-label").takeIf { it.isNotEmpty() }!!
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?: element.selectFirst("img")?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        // Check if there is actually a next page button that is not disabled
        val hasNextPage = mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ======================= Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val hasFilters = filters.isNotEmpty() && (
            filters.filterIsInstance<UriPartFilter>().any { it.state != 0 } ||
                filters.filterIsInstance<GenreGroup>().any { group -> group.state.any { it.state } }
            )

        // If there is only a query and no active filters, use the fast API endpoint
        if (query.isNotEmpty() && !hasFilters) {
            val url = "$baseUrl/api/mangas/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "50")
                .build()
            return GET(url, headers)
        }

        // Otherwise, construct the list page URL with filters
        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is ContentTypeFilter -> {
                        val part = filter.toUriPart()
                        if (part.isNotEmpty()) addQueryParameter("tipo", part)
                    }
                    is FormatFilter -> {
                        val part = filter.toUriPart()
                        if (part != "both") addQueryParameter("formato", part)
                    }
                    is SortFilter -> {
                        val part = filter.toUriPart()
                        if (part != "latest") addQueryParameter("sort", part)
                    }
                    is GenreGroup -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.slug }
                        if (genres.isNotEmpty()) {
                            addQueryParameter("generos", genres)
                        }
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("api")) {
            val result = response.parseAs<SearchResponseDto>()
            val mangas = result.items.map { item ->
                SManga.create().apply {
                    url = "/${item.slug}"
                    title = item.titulo
                    thumbnail_url = item.portada?.let { portada ->
                        if (portada.startsWith("http")) portada else "$baseUrl$portada"
                    }
                }
            }
            return MangasPage(mangas, false)
        } else {
            return latestUpdatesParse(response)
        }
    }

    // ======================= Details =======================================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val bodyHtml = document.html()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text().replace("Vista Previa", "")
            description = document.selectFirst("p.whitespace-pre-line")?.text()

            // Thumbnail from og:image meta tag
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")

            // Genres from both HTML anchors and RSC payload
            genre = document.select("a[href*=/list?generos=]").joinToString { it.text() }

            // Artist & Author
            val htmlArtists = document.select("a[href*=/creador/]").joinToString { it.text() }
            if (htmlArtists.isNotEmpty()) {
                artist = htmlArtists
                author = htmlArtists
            }

            // Status
            val statusText = bodyHtml.lowercase()
            status = when {
                statusText.contains("finalizado") -> SManga.COMPLETED
                arrayOf("publicándose", "publicandose").any { statusText.contains(it) } -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // ======================= Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // Chapter links in the detail page HTML:
        // <a class="group block..." href="/reader/SLUG/N/cascade">
        //   <h3>Capítulo N</h3>
        // </a>
        val chapterLinks = document.select("a.group.block[href*=/reader/][href*=/cascade]")

        if (chapterLinks.isNotEmpty()) {
            return chapterLinks.map { element ->
                val href = element.absUrl("href")
                val chapterText = element.selectFirst("h3")?.text() ?: ""
                val chapterNum = CHAPTER_NUM_REGEX.find(href)?.groupValues?.get(1)

                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    name = chapterText.ifEmpty { "Chapter ${chapterNum ?: "1"}" }
                    chapter_number = chapterNum?.toFloatOrNull() ?: 0f
                }
            }
        }

        // Fallback for one-shots: find the "Leer" button
        val singleChapterBtn = document.selectFirst("a[href*=/cascade]:has(span:contains(Leer))")
            ?: document.selectFirst("a[href*=/reader/][href*=/cascade]")

        if (singleChapterBtn != null) {
            return listOf(
                SChapter.create().apply {
                    setUrlWithoutDomain(singleChapterBtn.absUrl("href"))
                    name = "Chapter 1"
                    chapter_number = 1f
                },
            )
        }

        return emptyList()
    }

    // ======================= Pages ========================================

    override fun pageListRequest(chapter: SChapter): Request {
        // Request the RSC payload which contains ALL pages in JSON.
        // The critical header is "RSC: 1" which tells Next.js to return
        // the React Server Component stream instead of full HTML.
        val url = baseUrl + chapter.url
        return GET(
            url,
            headers.newBuilder()
                .add("RSC", "1")
                .build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val pages = mutableListOf<Page>()

        // RSC payload contains: "pages":[{"orden":1,"imagen_url":"https://cdn.../1.webp"},...]
        // Use a greedy regex to capture the entire pages array content
        val matchResult = PAGES_REGEX.find(body)

        if (matchResult != null) {
            try {
                val pagesJson = "[${matchResult.groupValues[1]}]"
                val jsonArray = json.parseToJsonElement(pagesJson).jsonArray
                jsonArray.forEachIndexed { index, element ->
                    val url = element.jsonObject["imagen_url"]?.jsonPrimitive?.content
                    if (url != null) {
                        pages.add(Page(index, imageUrl = url))
                    }
                }
                if (pages.isNotEmpty()) return pages
            } catch (_: Exception) {
                // Fallback to HTML parsing
            }
        }

        // Fallback: try to extract imagen_url from any JSON-like text
        URL_REGEX.findAll(body).forEachIndexed { i, match ->
            pages.add(Page(i, imageUrl = match.groupValues[1]))
        }
        if (pages.isNotEmpty()) return pages

        // Last resort: parse HTML for img tags with CDN manga-cap URLs
        val document = org.jsoup.Jsoup.parse(body, baseUrl)
        document.select("img[src*=manga-cap], img[src*=cdn.codearctraducciones]").forEachIndexed { i, img ->
            val src = img.absUrl("src")
            if (src.isNotEmpty()) {
                pages.add(Page(i, imageUrl = src))
            }
        }
        return pages
    }

    override fun getFilterList(): FilterList = getFilters()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private companion object {
        const val POPULAR_MAX_PAGE = 5
        val CHAPTER_NUM_REGEX = """/reader/[^/]+/(\d+)/""".toRegex()
        val PAGES_REGEX = """"pages":\[(\{"orden".*?)\]""".toRegex()
        val URL_REGEX = """"imagen_url":"(https?://[^"]+)"""".toRegex()
    }
}
