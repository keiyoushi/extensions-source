package eu.kanade.tachiyomi.extension.es.jeazscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
class JeazScansSpanish(
    override val lang: String,
    override val id: Long,
) : JeazScans()

abstract class JeazScans : HttpSource() {

    override val name = "Jeaz Scans"

    override val baseUrl = "https://lectorhub.j5z.xyz"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    // The site migrated to custom home sections and PHP routes for search.
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section:has(h3:matchesOwn((?i)Top Rankings)) a[href*='manga.php?id=']").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.selectFirst("h4, h5")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/directorio.php?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".directory-grid a.directory-card[href*='manga.php?id=']")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    title = element.selectFirst("h3")!!.let { it.attr("title").ifBlank { it.text() } }
                    thumbnail_url = element.selectFirst(".directory-cover img")?.attr("abs:src")
                }
            }

        val hasNextPage = document.selectFirst(".directory-pagination a[aria-label='Página siguiente']") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.blood-title")!!.text()

            description = buildString {
                val descriptionBlock = document.selectFirst("div.text-gray-200:has(h3:matchesOwn((?i)SINOPSIS))")
                    ?: document.selectFirst("div.text-gray-200")
                descriptionBlock?.let {
                    append(it.ownText().ifEmpty { it.text().replace(SINOPSIS_REGEX, "") })
                }
            }

            thumbnail_url = document.selectFirst("div.lg\\:col-span-3 div.cultivation-panel img")?.attr("abs:src")

            genre = document.select("a[href*='directorio.php?genero=']").joinToString { it.text() }

            val statusText = document.selectFirst("span.status-badge")?.text().orEmpty().lowercase()
            if (statusText.isNotEmpty()) {
                status = when {
                    statusText.contains("complet") -> SManga.COMPLETED
                    arrayOf("pausa", "hiato").any { statusText.contains(it) } -> SManga.ON_HIATUS
                    arrayOf("cancel", "aband").any { statusText.contains(it) } -> SManga.CANCELLED
                    arrayOf("cultivo", "curso", "ongoing", "emision").any { statusText.contains(it) } -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val mangaId = extractMangaIdFromUrl(response.request.url.toString())
            ?: extractMangaIdFromScript(document)
            ?: throw Exception("Could not extract Jeaz Scans manga id from: ${response.request.url}")

        val slug = extractMangaSlug(document)
            ?: throw Exception("Could not extract Jeaz Scans manga slug")

        return fetchAllChapters(mangaId, slug)
    }

    private fun fetchAllChapters(mangaId: Int, slug: String): List<SChapter> {
        val pages = walkChapterPages { offset ->
            val request = buildChapterListRequest(mangaId, offset, CHAPTER_API_LIMIT)
            client.newCall(request).execute().use { apiResponse ->
                if (!apiResponse.isSuccessful) {
                    throw Exception("HTTP error ${apiResponse.code} fetching chapters")
                }
                val dto = apiResponse.parseAs<ChaptersPageDto>()
                if (!dto.success) throw Exception("Jeaz Scans chapters API returned error")
                dto.toChapterPage()
            }
        }
        return pages.flatMap { page ->
            page.chapters.mapNotNull { chapter -> chapter.toSChapter(slug, baseUrl) }
        }
    }

    private fun buildChapterListRequest(mangaId: Int, offset: Int, limit: Int): Request {
        val url = "$baseUrl/api_capitulos_manga.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("orden", "desc")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageElements = document.select(
            "#pagesContainer img.reader-page-image, .page-container img.protected-img, .reader-body img, .reading-content img",
        )

        val htmlPages = imageElements.mapNotNull { element ->
            val imageUrl = when {
                element.hasAttr("data-verify") -> decodeVerifyToUrl(element.attr("data-verify"))
                element.hasAttr("data-sec-src") -> element.attr("abs:data-sec-src")
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                else -> element.attr("abs:src")
            }

            imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

        if (htmlPages.isNotEmpty()) return htmlPages

        return fetchPagesFromApi(document)
    }

    private fun fetchPagesFromApi(document: Document): List<Page> {
        val (slug, cap) = extractSlugAndCap(document) ?: throw Exception("Could not extract slug/cap for API")
        val apiUrl = buildApiUrl(document.location(), slug, cap) ?: throw Exception("Could not build API URL")

        val requestHeaders = headers.newBuilder()
            .set("Referer", document.location())
            .build()

        val payload = client.newCall(GET(apiUrl, requestHeaders)).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error ${response.code}")
            }

            val apiResponse = response.parseAs<ApiLectorResponse>()
            if (!apiResponse.success) throw Exception("API returned error")

            apiResponse
        }

        val pages = payload.paginas

        return pages.filter { it.dataVerify.isNotBlank() }
            .sortedBy { it.orden }
            .mapNotNull { decodeVerifyToUrl(it.dataVerify) }
            .distinct()
            .mapIndexed { idx, imageUrl -> Page(idx, imageUrl = imageUrl) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isBlank()) {
        latestUpdatesRequest(page)
    } else {
        val url = "$baseUrl/ajax_search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()
        GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.encodedPath.endsWith("/ajax_search.php")) {
            return latestUpdatesParse(response)
        }

        val items = response.parseAs<List<SearchResponseItem>>()
        val mangas = items.mapNotNull { it.toSManga(baseUrl) }

        return MangasPage(mangas, false)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val CHAPTER_API_LIMIT = 20
        private val SINOPSIS_REGEX = Regex("^SINOPSIS:?\\s*", RegexOption.IGNORE_CASE)
    }
}
