package eu.kanade.tachiyomi.extension.en.sanascans

import android.content.SharedPreferences
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rx.Observable
import java.text.Normalizer
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class SanaScans : Iken(
    "Sana Scans",
    "en",
    "https://sanascans.com",
    "https://sanascans.com",
) {

    override val sortPagesByFilename = true
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val popularHeading = document.select("h1")
            .firstOrNull { it.text().trim().equals("Popular Today", ignoreCase = true) }
        val popularContainer = popularHeading?.parent()
        var sibling = popularContainer?.nextElementSibling()
        var popularSection: org.jsoup.nodes.Element? = null
        while (sibling != null) {
            if (sibling.select(".splide a[href*=\"/series/\"]").isNotEmpty()) {
                popularSection = sibling
                break
            }
            sibling = sibling.nextElementSibling()
        }

        val entries = (
            popularSection?.select(".splide a[href*=\"/series/\"]")
                ?: document.select("a[href*=\"/series/\"]")
            )
            .mapNotNull(::parseSeriesAnchor)
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/rss.xml?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        val entries = document.select("channel > item").mapNotNull(::parseRssItem)
        val paged = entries.drop((page - 1) * latestPageSize).take(latestPageSize)
        val hasNextPage = entries.size > page * latestPageSize

        return MangasPage(paged, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/series-sitemap.xml?page=$page&searchTerm=${encodeQuery(query)}", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val normalizedQuery = response.request.url.queryParameter("searchTerm").normalizeForSearch()

        val entries = if (normalizedQuery.isBlank()) {
            sitemapSeries
        } else {
            val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
            sitemapSeries.filter { series ->
                val searchableFields = listOf(series.title.normalizeForSearch(), series.slug.normalizeForSearch())
                tokens.all { token -> searchableFields.any { field -> field.contains(token) } }
            }
        }.map { it.toSManga() }

        val paged = entries.drop((page - 1) * searchPageSize).take(searchPageSize)
        val hasNextPage = entries.size > page * searchPageSize

        return MangasPage(paged, hasNextPage)
    }

    override fun getFilterList() = FilterList()

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/series/${manga.url.substringBeforeLast("#")}", headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                parseMangaDetails(response).apply { url = manga.url }
            }
    }

    private fun parseMangaDetails(response: Response): SManga {
        val body = response.body.string()
        val document = Jsoup.parse(body, response.request.url.toString())

        val title = document.selectFirst("h1[itemprop=name]")?.text()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" Manga - Sana scans")
            ?: document.title()

        val description = document.selectFirst("[itemprop=description]")?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: listOf("postContent", "description", "summary", "synopsis")
                .flatMap { extractJsonStrings(body, it) }
                .filter { it.length > 80 }
                .maxByOrNull { it.length }
                ?.let { Jsoup.parse(it).text() }
            ?: document.selectFirst("meta[name=description]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
        val thumbnailUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        val genres = runCatching { extractJsonArray(body, "genres").parseAs<List<GenreDto>>() }
            .getOrNull()
            ?.joinToString(", ") { it.name }

        val status = parseStatus(document)

        return SManga.create().apply {
            this.title = title
            this.description = description
            this.thumbnail_url = thumbnailUrl
            this.genre = genres
            this.status = status
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val seriesSlug = response.request.url.pathSegments
            .dropWhile { it != "series" }
            .drop(1)
            .firstOrNull()
            ?: response.request.url.pathSegments.lastOrNull()
            ?: ""

        val chaptersJson = extractJsonArray(body, "chapters")
        val chapters = chaptersJson.parseAs<List<ChapterDto>>()

        return chapters.mapNotNull { chapter ->
            val isLocked = chapter.isPermanentlyLocked == true ||
                (chapter.price ?: 0) > 0 ||
                chapter.unlockAt != null

            if (isLocked && !preferences.getBoolean(showLockedChapterPrefKey, false)) {
                return@mapNotNull null
            }

            chapter.toSChapter(seriesSlug, isLocked)
        }
    }

    private fun parseSeriesAnchor(element: org.jsoup.nodes.Element): SManga? {
        val href = element.attr("abs:href").ifBlank { element.attr("href") }
        val seriesUrl = sanitizeSeriesUrl(href) ?: return null
        val slug = seriesUrl.substringAfter("/series/").trimEnd('/')

        val title = element.selectFirst("img[alt]")?.attr("alt")
            ?.removePrefix("Cover of ")
            ?.removePrefix("cover of ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: element.text().trim().takeIf { it.isNotBlank() }
            ?: slugToTitle(slug)

        val thumbnailUrl = element.selectFirst("img[src]")?.let { img ->
            img.attr("abs:src").ifBlank { img.attr("src") }
        }

        return SManga.create().apply {
            this.url = slug
            this.title = title
            this.thumbnail_url = thumbnailUrl
        }
    }

    private fun parseRssItem(item: org.jsoup.nodes.Element): SManga? {
        val link = item.selectFirst("link")?.text()?.trim().orEmpty()
        val seriesUrl = sanitizeSeriesUrl(link) ?: return null
        val slug = seriesUrl.substringAfter("/series/").trimEnd('/')

        val title = item.selectFirst("title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: slugToTitle(slug)
        val description = item.selectFirst("description")?.text()?.let { Jsoup.parse(it).text() }

        return SManga.create().apply {
            this.url = slug
            this.title = title
            this.description = description
        }
    }

    private fun sanitizeSeriesUrl(url: String): String? {
        if (!url.contains("/series/")) return null
        if (url.contains("/chapter-")) return null
        if (url.contains("/rss")) return null
        return url.substringBefore('#').substringBefore('?')
    }

    private fun encodeQuery(query: String): String =
        java.net.URLEncoder.encode(query, "UTF-8")

    private fun slugToTitle(slug: String): String {
        val decoded = java.net.URLDecoder.decode(slug, "UTF-8")
        return decoded.replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
            }
    }

    private fun parseStatus(document: Document): Int {
        val statusText = document.select("h1")
            .firstOrNull { it.text().trim().equals("Status", ignoreCase = true) }
            ?.parent()
            ?.selectFirst("p")
            ?.text()
            ?.trim()
            ?.lowercase(Locale.ROOT)

        return when (statusText) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun extractJsonArray(body: String, key: String): String {
        val keyIndex = findJsonKeyIndex(body, key)
        if (keyIndex == -1) throw Exception("Unable to find $key data")

        val start = body.indexOf('[', keyIndex)
        if (start == -1) throw Exception("Unable to locate $key list")

        var depth = 1
        var i = start + 1
        while (i < body.length && depth > 0) {
            when (body[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }

        val raw = body.substring(start, i)
        return if (raw.contains("\\\"")) {
            "\"$raw\"".parseAs<String>()
        } else {
            raw
        }
    }

    private fun findJsonKeyIndex(body: String, key: String, startIndex: Int = 0): Int {
        val direct = body.indexOf("\"$key\":", startIndex)
        val escaped = body.indexOf("\\\"$key\\\":", startIndex)

        return when {
            direct == -1 -> escaped
            escaped == -1 -> direct
            else -> minOf(direct, escaped)
        }
    }

    private fun extractJsonStrings(body: String, key: String): List<String> {
        val results = mutableListOf<String>()
        var searchIndex = 0

        while (searchIndex < body.length) {
            val keyIndex = findJsonKeyIndex(body, key, searchIndex)
            if (keyIndex == -1) break

            extractJsonStringAt(body, keyIndex)?.let(results::add)
            searchIndex = keyIndex + 1
        }

        return results
    }

    private fun extractJsonStringAt(body: String, keyIndex: Int): String? {
        val colonIndex = body.indexOf(':', keyIndex)
        if (colonIndex == -1) return null

        var i = colonIndex + 1
        while (i < body.length && body[i].isWhitespace()) i++
        if (i >= body.length) return null

        if (body[i] == '\\' && body.getOrNull(i + 1) == '"') {
            val start = i + 2
            var j = start
            while (j < body.length - 1) {
                if (body[j] == '\\' && body[j + 1] == '"') {
                    var backslashes = 0
                    var k = j - 1
                    while (k >= start && body[k] == '\\') {
                        backslashes++
                        k--
                    }
                    if (backslashes % 2 == 0) {
                        return "\"${body.substring(start, j)}\"".parseAs<String>()
                    }
                }
                j++
            }
            return null
        }

        if (body[i] != '"') return null

        val start = i
        i++
        var escaped = false
        while (i < body.length) {
            val c = body[i]
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                break
            }
            i++
        }

        if (i >= body.length) return null
        return body.substring(start, i + 1).parseAs<String>()
    }

    private fun String?.normalizeForSearch(): String {
        if (this.isNullOrBlank()) return ""

        val base = Normalizer.normalize(this, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(diacriticsRegex, "")

        val collapsed = nonAlphanumericRegex.replace(base, " ").trim()

        return multiSpaceRegex.replace(collapsed, " ").trim()
    }

    companion object {
        private val diacriticsRegex = Regex("\\p{M}+")
        private val nonAlphanumericRegex = Regex("[^a-z0-9]+")
        private val multiSpaceRegex = Regex("\\s+")
    }

    private val sitemapSeries: List<SitemapSeries> by lazy {
        val response = client.newCall(GET("$baseUrl/series-sitemap.xml", headers)).execute()
        val document = response.asJsoup()

        document.select("url").mapNotNull { element ->
            val loc = element.selectFirst("loc")?.text()?.trim().orEmpty()
            val seriesUrl = sanitizeSeriesUrl(loc) ?: return@mapNotNull null
            val slug = seriesUrl.substringAfter("/series/").trimEnd('/')

            val title = element.selectFirst("image\\:title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::slugToTitle)
                ?: slugToTitle(slug)

            val thumbnailUrl = element.selectFirst("image\\:loc")?.text()?.trim()

            SitemapSeries(slug, title, thumbnailUrl)
        }
    }
}

@Serializable
private data class ChapterDto(
    val slug: String,
    val number: JsonPrimitive,
    val title: String? = null,
    val createdAt: String? = null,
    val isPermanentlyLocked: Boolean? = null,
    val unlockAt: String? = null,
    val price: Int? = null,
) {
    fun toSChapter(seriesSlug: String, locked: Boolean): SChapter {
        val numberText = number.content
        val chapterTitle = title?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        val prefix = if (locked) "🔒 " else ""

        return SChapter.create().apply {
            url = "/series/$seriesSlug/$slug"
            name = "${prefix}Chapter $numberText$chapterTitle"
            date_upload = createdAt?.let {
                try {
                    dateFormat.parse(it)?.time ?: 0L
                } catch (_: ParseException) {
                    0L
                }
            } ?: 0L
        }
    }
}

@Serializable
private data class GenreDto(
    val name: String,
)

private data class SitemapSeries(
    val slug: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SitemapSeries.title
        thumbnail_url = thumbnailUrl
    }
}

private const val latestPageSize = 20
private const val searchPageSize = 30
private const val showLockedChapterPrefKey = "pref_show_locked_chapters"
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
