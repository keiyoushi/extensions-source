package eu.kanade.tachiyomi.extension.es.mangalect

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
class MangaLect(
    override val lang: String,
    override val id: Long,
) : HttpSource() {

    override val name = "MangaLect"

    override val baseUrl = "https://mangalect.org"

    override val supportsLatest = true

    private val assetMirror = "https://images.mangalect.org/file/leermangaesp"

    private val json = Json { ignoreUnknownKeys = true }

    // ──── Headers ────

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0")

    // ──── Popular (Tendencias · no pagination) ────

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val scriptData = document.select("script")
            .map { it.data() }
            .firstOrNull { it.trimStart().startsWith("[{\"id\"") }
            ?: return MangasPage(emptyList(), false)

        val items = json.decodeFromString<List<TrendEntry>>(scriptData)

        val mangas = items.map { entry ->
            SManga.create().apply {
                title = entry.titulo
                url = entry.slug
                thumbnail_url = "$assetMirror/${entry.portada}"
            }
        }

        return MangasPage(mangas, false)
    }

    // ──── Latest Updates (Últimas Publicaciones · paginated) ────

    // The API returns ALL items in one flat array with no server-side pagination.
    // We pass a _page tracking param so the parse method can slice the correct page.
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/latest_chapters_with_dates/".toHttpUrl().newBuilder()
            .addQueryParameter("_page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val allItems = response.parseAs<List<LatestEntry>>()
        val page = response.request.url.queryParameter("_page")?.toIntOrNull() ?: 1

        val pageSize = 20
        val startIndex = (page - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, allItems.size)
        val hasNextPage = endIndex < allItems.size

        if (startIndex >= allItems.size) {
            return MangasPage(emptyList(), false)
        }

        val mangas = allItems.subList(startIndex, endIndex).map { entry ->
            SManga.create().apply {
                title = entry.titulo
                url = entry.slug
                thumbnail_url = "$assetMirror/${entry.portada}"
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ──── Search ────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/buscar_mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "20")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        val mangas = result.resultados.map { entry ->
            SManga.create().apply {
                title = entry.titulo
                url = entry.slug
                thumbnail_url = "$assetMirror/${entry.portada}"
            }
        }

        return MangasPage(mangas, page < result.total_pages)
    }

    // ──── Manga Details ────

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/info/${manga.url}/", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/info/${manga.url}/"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")?.text()
                ?: throw Exception("Título no encontrado")

            // Author not available on this site
            author = "Desconocido"

            // Cover: prefer img.manga-cover, fallback to body data attribute
            thumbnail_url = document.selectFirst("img.manga-cover[src]")
                ?.attr("abs:src")
                ?: document.body().attr("data-portada-rel")
                    .takeIf { it.isNotBlank() }
                    ?.let { "$assetMirror/$it" }

            // Status
            status = parseStatus(document.selectFirst("span.status-text")?.text())

            // Genres
            genre = document.select("#info-generos a.genero-item")
                .joinToString(", ") { it.text() }

            // Description
            description = document.selectFirst("section.synopsis p#synopsis-text")
                ?.text()
        }
    }

    private fun parseStatus(text: String?): Int = when (text?.trim()?.lowercase()) {
        "en curso" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "abandonado" -> SManga.CANCELLED
        "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ──── Chapter List ────

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/info/${manga.url}/", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        val seen = linkedSetOf<String>()
        val chapters = mutableListOf<SChapter>()

        var currentUrl = response.request.url
        var currentDocument = response.asJsoup()
        val slug = currentDocument.body().attr("data-manga-slug")

        if (slug.isBlank()) return emptyList()

        val visitedPages = mutableSetOf(currentUrl.toString())
        var pageCount = 1

        while (pageCount <= MAX_PAGINATION_PAGES) {
            currentDocument.select("div#chapter-list div.chapter-card a.chapter-link[data-chapter]")
                .forEach { link ->
                    val chapterNum = link.attr("data-chapter")
                    if (chapterNum.isEmpty()) return@forEach

                    val chapterTitle = link.selectFirst("div.chapter-title")?.text()
                        ?: return@forEach
                    val chapterDate = link.selectFirst("div.chapter-date")?.text()
                    val chapterUrl = "$slug/$chapterNum"

                    if (seen.add(chapterUrl)) {
                        chapters += SChapter.create().apply {
                            name = chapterTitle
                            url = chapterUrl
                            date_upload = try {
                                chapterDate?.let { dateFormat.parse(it)?.time ?: 0L } ?: 0L
                            } catch (_: Exception) {
                                0L
                            }
                        }
                    }
                }

            val nextUrl = currentDocument.selectFirst("a#more-link[href]")
                ?.attr("href")
                ?.takeIf(String::isNotBlank)
                ?.let { currentUrl.resolve(it) }
                ?: break

            val nextUrlStr = nextUrl.toString()
            if (!visitedPages.add(nextUrlStr)) break

            client.newCall(GET(nextUrl, headers)).execute().use { nextResponse ->
                currentUrl = nextResponse.request.url
                currentDocument = nextResponse.asJsoup()
            }
            pageCount++
        }

        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/lectura/${chapter.url}/"

    // ──── Page List (Images) ────

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("/", limit = 2)
        val slug = parts[0]
        val chapterNum = parts.getOrElse(1) { chapter.url }
        return GET("$baseUrl/lectura/$slug/$chapterNum/", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.manga-image[src]")
            .map { it.attr("abs:src") }
            .filter { it.contains("images.mangalect.org") }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ──── Serialization DTOs ────

    /** Inline script JSON item on the homepage (Tendencias / popular). */
    @Serializable
    data class TrendEntry(
        val id: Long = 0,
        val slug: String = "",
        val titulo: String = "",
        val portada: String = "",
        val tipo: String = "",
        val demografia: String = "",
        val genero: String = "",
        val visitas: Long = 0,
        val ultimo_capitulo: Double = 0.0,
    )

    /** Item from /api/latest_chapters_with_dates/ (Últimas Publicaciones / latest). */
    @Serializable
    data class LatestEntry(
        val slug: String = "",
        val titulo: String = "",
        val portada: String = "",
        val ultimo_capitulo: Double = 0.0,
    )

    /** Response from /api/buscar_mangas/ (search). */
    @Serializable
    data class SearchResponse(
        val resultados: List<SearchResult> = emptyList(),
        val total_pages: Int = 1,
    )

    @Serializable
    data class SearchResult(
        val slug: String = "",
        val titulo: String = "",
        val portada: String = "",
    )

    companion object {
        private const val MAX_PAGINATION_PAGES = 30
    }
}
