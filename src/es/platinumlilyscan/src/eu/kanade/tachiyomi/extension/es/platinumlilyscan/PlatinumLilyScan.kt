package eu.kanade.tachiyomi.extension.es.platinumlilyscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Platinum Lily Scan — https://platinumlilyscan.com
 *
 * Spanish yuri/GL scanlation built with Next.js App Router + RSC.
 *
 * ─── KEY FINDINGS (confirmed by Python diagnostic) ──────────────────────────
 *
 * 1. CATALOG / DETAILS / CHAPTER LIST — rendered server-side in the HTML.
 *    Standard OkHttp GET + JSoup parse works perfectly.
 *
 * 2. PAGE LIST — the chapter reader is a 'use client' React component.
 *    A plain HTML GET returns only UI images (logos). Chapter images are NOT
 *    in the HTML. The client-side JS fetches from an internal REST API:
 *
 *      GET /api/series/{slug}
 *
 *    This returns a JSON object for the full series, which includes ALL chapters
 *    with their page image URLs. The image URL pattern is:
 *      /uploads/chapters/{slug}/{chapter-number}/{page}.webp
 *
 *    We pass the chapter number as a query param (?chapter=N) so we can read
 *    it back in pageListParse without needing extra state.
 * ────────────────────────────────────────────────────────────────────────────
 */
class PlatinumLilyScan : HttpSource() {

    override val name = "Platinum Lily Scan"

    override val baseUrl = "https://platinumlilyscan.com"

    override val lang = "es"

    override val supportsLatest = true

    // ---------------------------------------------------------------------------
    // Date parsing
    // Dates look like "21 abr" or "29 mar" (day + Spanish month abbr, no year).
    // ---------------------------------------------------------------------------

    private val dateFormat by lazy {
        SimpleDateFormat("d MMM yyyy", Locale("es"))
    }

    private val currentYear: Int
        get() = Calendar.getInstance().get(Calendar.YEAR)

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return runCatching { dateFormat.tryParse("$dateStr $currentYear") }.getOrDefault(0L)
    }

    // ---------------------------------------------------------------------------
    // Popular Manga — /browse
    // ---------------------------------------------------------------------------

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document
            .select("a[href*='/series/']")
            .filter { !it.attr("href").contains("/chapter/") }
            .distinctBy { it.attr("href") }
            .map { mangaFromElement(it) }

        val hasNextPage =
            document.selectFirst("a[rel=next], nav .next, [aria-label*=siguiente]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.attr("href").let {
            if (it.startsWith("http")) it.removePrefix(baseUrl) else it
        }
        title = element.selectFirst("h3, h2, [class*=title]")?.text()?.trim()
            ?: element.text().trim()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // ---------------------------------------------------------------------------
    // Latest Updates — home page
    // ---------------------------------------------------------------------------

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document
            .select("a[href*='/series/']")
            .filter { !it.attr("href").contains("/chapter/") }
            .distinctBy { it.attr("href") }
            .map { mangaFromElement(it) }

        return MangasPage(mangas, false)
    }

    // ---------------------------------------------------------------------------
    // Search — /browse?q={query}&genre={genre}&status={status}&type={type}
    // ---------------------------------------------------------------------------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()

        if (query.isNotBlank()) url.addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> if (filter.state != 0) url.addQueryParameter("genre", filter.toUriPart())
                is StatusFilter -> if (filter.state != 0) url.addQueryParameter("status", filter.toUriPart())
                is TypeFilter -> if (filter.state != 0) url.addQueryParameter("type", filter.toUriPart())
                else -> {}
            }
        }

        if (page > 1) url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ---------------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------------

    private class GenreFilter :
        UriPartFilter(
            "Género",
            arrayOf(
                Pair("Todos", ""),
                Pair("Yuri", "Yuri"),
                Pair("Romance", "Romance"),
                Pair("Comedia", "Comedia"),
                Pair("Escolar", "Escolar"),
                Pair("Ciencia Ficción", "Ciencia Ficción"),
                Pair("Acción", "Acción"),
                Pair("Fantasía", "Fantasía"),
                Pair("Drama", "Drama"),
                Pair("Vida cotidiana", "Vida cotidiana"),
                Pair("+18", "+18"),
            ),
        )

    private class StatusFilter :
        UriPartFilter(
            "Estado",
            arrayOf(
                Pair("Todos", ""),
                Pair("Publicándose", "Publicándose"),
                Pair("Finalizado", "Finalizado"),
                Pair("Hiatus", "Hiatus"),
            ),
        )

    private class TypeFilter :
        UriPartFilter(
            "Tipo",
            arrayOf(
                Pair("Todos", ""),
                Pair("Manga", "Manga"),
                Pair("Manhwa", "Manhwa"),
                Pair("Manhua", "Manhua"),
                Pair("Novela", "Novela"),
                Pair("Doujinshi", "Doujinshi"),
                Pair("One-Shot", "One-Shot"),
            ),
        )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros no se aplican si hay texto de búsqueda"),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
    )

    // ---------------------------------------------------------------------------
    // Manga Details — SSR HTML works fine; use og: meta tags (more reliable than h1)
    // ---------------------------------------------------------------------------

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            // og:title is more reliable than h1 on Next.js pages.
            // On series pages it returns the manga name; h1 can return the site name.
            title = document.selectFirst("meta[property='og:title']")?.attr("content")
                ?.removeSuffix(" | Platinum Lily Scan")?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: ""

            thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("img[src*='/uploads/covers/']")?.attr("abs:src")

            description = document.selectFirst("meta[property='og:description']")?.attr("content")
                ?: document.selectFirst("meta[name='description']")?.attr("content")

            genre = document.select("a[href*='genre=']")
                .joinToString(", ") { it.text().trim() }

            val bodyText = document.body()?.text() ?: ""
            status = when {
                bodyText.contains("Publicándose", ignoreCase = true) -> SManga.ONGOING
                bodyText.contains("Finalizado", ignoreCase = true) -> SManga.COMPLETED
                bodyText.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val contributors = document.select("a[href*='browse?q=']")
            author = contributors.getOrNull(0)?.text()?.trim()
            artist = contributors.getOrNull(1)?.text()?.trim() ?: author

            initialized = true
        }
    }

    // ---------------------------------------------------------------------------
    // Chapter List — SSR HTML: chapters are <a href="/series/{slug}/chapter/{num}">
    // ---------------------------------------------------------------------------

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a[href*='/chapter/']").map { element ->
            SChapter.create().apply {
                val href = element.attr("href")
                url = if (href.startsWith("http")) href.removePrefix(baseUrl) else href

                val chNum = url.substringAfterLast("/chapter/")
                chapter_number = chNum.toFloatOrNull() ?: -1f
                name = "Capítulo $chNum"

                val dateText = element.select("*").lastOrNull()?.ownText()?.trim() ?: ""
                date_upload = parseDate(dateText)
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ---------------------------------------------------------------------------
    // Page List — REST API approach
    //
    // Confirmed via Python diagnostic:
    //   GET /api/series/{slug}  →  JSON with ALL chapters + image URLs
    //
    // Image URL format:  /uploads/chapters/{slug}/{chapter-number}/{page}.webp
    //
    // Strategy:
    //   1. Build request for /api/series/{slug}
    //   2. Encode chapter number in a query param so pageListParse can read it
    //   3. In pageListParse, regex-filter only the images for that chapter
    // ---------------------------------------------------------------------------

    override fun pageListRequest(chapter: SChapter): Request {
        // chapter.url format: /series/{slug}/chapter/{num}  (e.g. /series/stardust-telepath/chapter/4)
        val slug = chapter.url
            .substringAfter("/series/")
            .substringBefore("/chapter/")

        val chNum = chapter.url.substringAfterLast("/chapter/")

        // Pass chapter number as query param — the server ignores unknown params
        // and returns the full series JSON; we read it back in pageListParse.
        val apiUrl = "$baseUrl/api/series/$slug".toHttpUrl().newBuilder()
            .addQueryParameter("chapter", chNum)
            .build()

        return GET(apiUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        // Recover chapter number and slug from the request URL
        val chNum = response.request.url.queryParameter("chapter") ?: return emptyList()
        val slug = response.request.url.encodedPath.substringAfterLast("/")

        val body = response.body.string()

        // Filter images that belong exclusively to the requested chapter.
        // Pattern: /uploads/chapters/{slug}/{chNum}/{page}.webp
        // Regex.escape handles chapter numbers like "12.1" or "46.5".
        val pattern = Regex(
            """/uploads/chapters/${Regex.escape(slug)}/${Regex.escape(chNum)}/[^"'\s\\]+\.(?:webp|jpg|jpeg|png|gif)""",
        )

        val pages = pattern.findAll(body)
            .map { "$baseUrl${it.value}" }
            .distinct()
            .toList()

        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun Response.asJsoup(): Document =
        Jsoup.parse(body.string(), request.url.toString())
}
