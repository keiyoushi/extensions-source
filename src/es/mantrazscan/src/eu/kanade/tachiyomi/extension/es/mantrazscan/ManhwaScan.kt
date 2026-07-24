package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.Locale

@Source
abstract class ManhwaScan : HttpSource() {

    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "*/*")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = catalogRequest(page, type = "")

    override fun popularMangaParse(response: Response): MangasPage = parseCatalog(response)

    // ============================== Latest =================================
    // The site doesn't expose a separate "latest" listing we could confirm, so this
    // reuses the same catalog as Popular. Search by title works fine regardless.
    override fun latestUpdatesRequest(page: Int): Request = catalogRequest(page, type = "")

    override fun latestUpdatesParse(response: Response): MangasPage = parseCatalog(response)

    // ============================== Search ==================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
            return GET(url, headers)
        }

        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
        return catalogRequest(page, type, genre)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.isApiSearch()) {
            val results = response.parseAs<SearchResponse>().results
            return MangasPage(results.map { it.toSManga(baseUrl) }, false)
        }
        return parseCatalog(response)
    }

    private fun catalogRequest(page: Int, type: String, genre: String = ""): Request {
        val basePath = if (type.isBlank()) "explorar" else "explorar/$type"
        val path = if (page <= 1) "$basePath/" else "$basePath/page/$page/"
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            if (genre.isNotBlank()) addQueryParameter("genero", genre)
        }.build()
        return GET(url, headers)
    }

    private fun parseCatalog(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href*=/manga/]")
            .filterNot { it.attr("href").contains("/capitulo-") }
            .distinctBy { it.attr("href") }
            .mapNotNull { it.toSMangaOrNull() }
        val hasNextPage = document.selectFirst("a:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSMangaOrNull(): SManga? {
        val href = attr("abs:href")
        val slug = href.substringAfter("/manga/").trim('/')
        if (slug.isBlank() || slug.contains("/")) return null
        val img = selectFirst("img")
        val name = img?.attr("alt")?.takeIf { it.isNotBlank() } ?: text().trim()
        if (name.isBlank()) return null
        return SManga.create().apply {
            url = slug
            title = name
            thumbnail_url = img?.attr("abs:src")
        }
    }

    // ============================== Details ================================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${mangaSlug(manga.url)}/"

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (isLegacyUrl(manga.url)) return migrationRequest(manga.title)
        return GET("$baseUrl/manga/${manga.url}/", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.isApiSearch()) {
            val match = findSearchMatch(response)
            val details = client.newCall(GET("$baseUrl/manga/${match.slug}/", headers)).execute()
            return details.asJsoup().toSMangaDetails(match.slug)
        }
        val slug = response.request.url.encodedPath.substringAfter("/manga/").trim('/')
        return response.asJsoup().toSMangaDetails(slug)
    }

    private fun Document.toSMangaDetails(slug: String): SManga = SManga.create().apply {
        url = slug
        val name = selectFirst("h1")?.text().orEmpty()
        title = name
        thumbnail_url = select("img").firstOrNull { it.attr("alt") == name }?.attr("abs:src")
        genre = select("a[href*=/genero/]").joinToString(", ") { it.text() }.takeIf { it.isNotBlank() }
        description = selectFirst("meta[name=description]")?.attr("content")
        val bodyText = text()
        status = when {
            bodyText.contains("En emisión", ignoreCase = true) -> SManga.ONGOING
            bodyText.contains("Completado", ignoreCase = true) || bodyText.contains("Completo", ignoreCase = true) -> SManga.COMPLETED
            bodyText.contains("Pausado", ignoreCase = true) || bodyText.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters =================================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}/"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.isApiSearch()) {
            val match = findSearchMatch(response)
            val details = client.newCall(GET("$baseUrl/manga/${match.slug}/", headers)).execute()
            return details.asJsoup().toSChapterList(match.slug)
        }
        val slug = response.request.url.encodedPath.substringAfter("/manga/").trim('/')
        return response.asJsoup().toSChapterList(slug)
    }

    private fun Document.toSChapterList(slug: String): List<SChapter> = select("a[href*=/manga/$slug/capitulo-]")
        .filterNot {
            val href = it.attr("href")
            val text = it.text().lowercase()

            href.endsWith("/capitulo-0/") ||
                text.contains("leer desde el inicio") ||
                text.contains("último capítulo")
        }
        .distinctBy { it.attr("href") }
        .mapNotNull { a ->
            val href = a.attr("href")
            val chapterPath = href.substringAfter("/manga/$slug/").trim('/')
            val num = chapterPath.substringAfter("capitulo-").toFloatOrNull() ?: return@mapNotNull null
            SChapter.create().apply {
                url = "$slug/$chapterPath"
                name = "Capítulo " + if (num == num.toLong().toFloat()) num.toLong().toString() else num.toString()
                chapter_number = num
                date_upload = parseRelativeDate(a.text())
            }
        }
        .sortedByDescending { it.chapter_number }

    // =============================== Pages ==================================
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/manga/${chapter.url}/", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[src*=/img/WP-manga/data/]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters =================================
    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
    )

    // ============================= Utilities =================================
    private fun Response.isApiSearch() = request.url.encodedPath.startsWith("/api/search")

    // Entries added by the previous version of the extension used "id#slug" as the
    // manga url. There is no way to translate the old numeric id anymore since the
    // old API is gone, so those (and any other unrecognized format) fall back to a
    // one-time title search to relink the entry to its new slug.
    private fun isLegacyUrl(url: String) = url.contains("#") || url.startsWith("/")

    private fun mangaSlug(url: String) = if (isLegacyUrl(url)) url.substringAfter("#") else url

    private fun migrationRequest(title: String): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", title)
            .build()
        return GET(url, headers)
    }

    private fun findSearchMatch(response: Response): SearchResultDto {
        val query = response.request.url.queryParameter("q").orEmpty()
        val results = response.parseAs<SearchResponse>().results
        return results.find { it.title.equals(query, ignoreCase = true) }
            ?: results.find { it.title.contains(query, ignoreCase = true) }
            ?: throw Exception("No se pudo migrar '$query'. Bórrala y búscala de nuevo desde la app.")
    }

    private fun parseRelativeDate(text: String): Long {
        val match = Regex("hace\\s+(\\d+)\\s*(\\p{L}+)", RegexOption.IGNORE_CASE).find(text) ?: return 0L
        val amount = match.groupValues[1].toIntOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase(Locale.ROOT)

        val field = when {
            unit.startsWith("min") -> Calendar.MINUTE
            unit.startsWith("h") -> Calendar.HOUR
            unit.startsWith("d") -> Calendar.DAY_OF_MONTH
            unit.startsWith("sem") -> Calendar.WEEK_OF_YEAR
            unit.startsWith("mes") -> Calendar.MONTH
            unit.startsWith("añ") || unit.startsWith("an") -> Calendar.YEAR
            else -> return 0L
        }

        return Calendar.getInstance().apply { add(field, -amount) }.timeInMillis
    }
}
