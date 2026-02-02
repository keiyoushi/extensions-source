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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import java.text.Normalizer
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

        val popularHeading = document.selectFirst("h1:matchesOwn((?i)Popular Today)")
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
        GET("$baseUrl/rss.xml", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoupXml()
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
        val rawQuery = response.request.url.queryParameter("searchTerm")
        val normalizedQuery = if (rawQuery == null) "" else rawQuery.normalizeForSearch()

        val sitemapEntries = parseSitemapSeries(response)
        val entries = if (normalizedQuery.isBlank()) {
            sitemapEntries
        } else {
            val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
            sitemapEntries.filter { series ->
                val searchableFields = listOf(series.title.normalizeForSearch(), series.slug.normalizeForSearch())
                tokens.all { token -> searchableFields.any { field -> field.contains(token) } }
            }
        }.map { it.toSManga() }

        val paged = entries.drop((page - 1) * searchPageSize).take(searchPageSize)
        val hasNextPage = entries.size > page * searchPageSize

        return MangasPage(paged, hasNextPage)
    }

    override fun getFilterList() = FilterList()

    override fun mangaDetailsRequest(manga: SManga) = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url.substringBefore('#'))
            .build(),
        headers,
    )

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
            ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" Manga - Sana scans")
            ?: document.title()

        val description = document.selectFirst("[itemprop=description]")?.text()
            ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
                ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("meta[name=twitter:description]")?.attr("content")
                ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("meta[name=description]")?.attr("content")
                ?.takeIf(String::isNotEmpty)
            ?: run {
                val jsonLdDescriptions = extractJsonLdDescriptions(document)
                val jsonLdCandidate = jsonLdDescriptions
                    .map { Jsoup.parse(it).text().trim() }
                    .firstOrNull { it.isNotEmpty() && looksLikeDescription(it) }

                if (!jsonLdCandidate.isNullOrEmpty()) {
                    return@run jsonLdCandidate
                }

                extractPostContent(body)
            }
        val thumbnailUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        val genres = runCatching { extractJsonArray(body, "genres").parseAs<List<GenreDto>>() }
            .getOrNull()
            ?.joinToString { it.name }

        val status = parseStatus(document)

        return SManga.create().apply {
            this.title = title
            this.description = description ?: ""
            this.thumbnail_url = thumbnailUrl
            this.genre = genres
            this.status = status
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val seriesSlug = seriesSlug(response.request.url) ?: ""

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
        val slug = seriesSlug(seriesUrl) ?: return null

        val title = element.selectFirst("img[alt]")?.attr("alt")
            ?.removePrefix("Cover of ")
            ?.removePrefix("cover of ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: element.text().takeIf(String::isNotEmpty)
            ?: slugToTitle(slug)

        val thumbnailUrl = element.selectFirst("img[src]")?.let { img ->
            img.attr("abs:src")
        }

        return SManga.create().apply {
            this.url = slug
            this.title = title
            this.thumbnail_url = thumbnailUrl
        }
    }

    private fun parseRssItem(item: org.jsoup.nodes.Element): SManga? {
        val link = item.selectFirst("link")?.text()
        if (link.isNullOrEmpty()) return null
        val seriesUrl = sanitizeSeriesUrl(link) ?: return null
        val slug = seriesSlug(seriesUrl) ?: return null

        val title = item.selectFirst("title")?.text()?.takeIf(String::isNotEmpty)
            ?: slugToTitle(slug)
        val description = item.selectFirst("description")?.text()?.let { Jsoup.parse(it).text() }

        return SManga.create().apply {
            this.url = slug
            this.title = title
            this.description = description
        }
    }

    private fun sanitizeSeriesUrl(url: String): HttpUrl? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val segments = httpUrl.pathSegments.filter { it.isNotEmpty() }
        val seriesIndex = segments.indexOf("series")
        if (seriesIndex == -1 || seriesIndex + 1 >= segments.size) return null
        if (segments.any { it.startsWith("chapter-") }) return null
        if (segments.contains("rss")) return null
        return httpUrl.newBuilder().fragment(null).query(null).build()
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
        val statusText = document.selectFirst("div:has(> h1:matchesOwn((?i)Status)) p")
            ?.text()
            ?.lowercase(Locale.ROOT)

        return when (statusText) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun extractJsonArray(body: String, key: String): String {
        val patterns = listOf(
            Regex(""""$key"\s*:\s*(\[[\s\S]*?])""", RegexOption.DOT_MATCHES_ALL),
            Regex("""\\\"$key\\\"\s*:\s*(\[[\s\S]*?])""", RegexOption.DOT_MATCHES_ALL),
        )

        val match = patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(body)?.groupValues?.getOrNull(1)
        }

        val raw = match ?: throw Exception("Unable to find $key data")
        return if (raw.contains("\\\"")) {
            "\"$raw\"".parseAs<String>()
        } else {
            raw
        }
    }

    private fun extractPostContent(body: String): String? {
        val patterns = listOf(
            Regex(""""postContent"\s*:\s*"((?:\\.|[^"])*)""""),
            Regex("""\\\"postContent\\\"\s*:\s*\\\"((?:\\\\.|[^\\"])*)\\\""""),
        )

        var best: String? = null
        for (pattern in patterns) {
            val matches = pattern.findAll(body)
            for (match in matches) {
                val raw = match.groupValues.getOrNull(1) ?: continue
                val decoded = parseJsonStringLiteral(raw) ?: raw
                val text = Jsoup.parse(decoded).text().trim()
                if (text.isNotEmpty() && looksLikeDescription(text)) {
                    if (best == null || text.length > best.length) {
                        best = text
                    }
                }
            }
        }

        return best
    }

    private fun parseJsonStringLiteral(raw: String): String? {
        val candidate = "\"$raw\""
        return runCatching { candidate.parseAs<String>() }.getOrNull()
    }

    private fun parseSitemapSeries(response: Response): List<SitemapSeries> {
        val document = response.asJsoupXml()

        return document.select("url").mapNotNull { element ->
            val loc = element.selectFirst("loc")?.text()
            if (loc.isNullOrEmpty()) return@mapNotNull null
            val seriesUrl = sanitizeSeriesUrl(loc) ?: return@mapNotNull null
            val slug = seriesSlug(seriesUrl) ?: return@mapNotNull null

            val title = element.selectFirst("image\\:title")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let(::slugToTitle)
                ?: slugToTitle(slug)

            val thumbnailUrl = element.selectFirst("image\\:loc")?.text()

            SitemapSeries(slug, title, thumbnailUrl)
        }
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
}

private fun extractJsonLdDescriptions(document: Document): List<String> {
    val scripts = document.select("script[type=\"application/ld+json\"]")
    if (scripts.isEmpty()) return emptyList()

    return scripts.flatMap { script ->
        val raw = script.data().takeIf(String::isNotEmpty) ?: return@flatMap emptyList()
        val element = runCatching { raw.parseAs<JsonElement>() }.getOrNull() ?: return@flatMap emptyList()
        collectJsonLdDescriptions(element)
    }.distinct()
}

private fun collectJsonLdDescriptions(element: JsonElement): List<String> {
    return when (element) {
        is JsonObject -> {
            val current = element["description"]?.asString()
            val nested = element.values.flatMap(::collectJsonLdDescriptions)
            if (current == null) nested else listOf(current) + nested
        }
        is JsonArray -> element.flatMap(::collectJsonLdDescriptions)
        else -> emptyList()
    }
}

private fun JsonElement.asString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

private fun looksLikeDescription(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 20) return false
    if (trimmed.contains("\"@id\"") || trimmed.contains("\"@type\"")) return false
    if (trimmed.contains("mainEntityOfPage") || trimmed.contains("chaptersPricing")) return false

    val letterCount = trimmed.count { it.isLetter() }
    if (letterCount < 20) return false

    val colonCount = trimmed.count { it == ':' }
    val quoteCount = trimmed.count { it == '"' || it == '\'' }
    val braceCount = trimmed.count { it == '{' || it == '}' || it == '[' || it == ']' }
    val commaCount = trimmed.count { it == ',' }
    val punctCount = colonCount + quoteCount + braceCount + commaCount
    val ratio = punctCount.toDouble() / trimmed.length

    return ratio <= 0.12
}
private fun Response.asJsoupXml(): Document {
    return Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())
}

private fun seriesSlug(url: HttpUrl): String? {
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    val seriesIndex = segments.indexOf("series")
    if (seriesIndex == -1 || seriesIndex + 1 >= segments.size) return null
    return segments[seriesIndex + 1]
}

private const val latestPageSize = 20
private const val searchPageSize = 30
private const val showLockedChapterPrefKey = "pref_show_locked_chapters"
