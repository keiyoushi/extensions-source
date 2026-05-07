package eu.kanade.tachiyomi.extension.en.sanascans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import java.text.Normalizer
import java.util.Locale

class SanaScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Sana Scans"
    override val lang = "en"
    override val baseUrl = "https://sanascans.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val sortPagesByFilename = true
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

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("figure:has(a[href*=\"/series/\"][href*=\"/chapter-\"])")
            .mapNotNull { figure ->
                figure.selectFirst("a[href*=\"/series/\"]:not([href*=\"/chapter-\"])")
                    ?.let(::parseSeriesAnchor)
            }
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val fragment = HttpUrl.Builder()
            .scheme("https")
            .host("localhost")
            .addQueryParameter("searchTerm", query)
            .build()
            .query
            .orEmpty()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series-sitemap.xml")
            .fragment(fragment)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val rawQuery = response.request.url.fragment
            ?.toFragmentQueryParameter("searchTerm")
        val normalizedQuery = if (rawQuery.isNullOrBlank()) "" else rawQuery.normalizeForSearch()

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

        return MangasPage(entries, false)
    }

    override fun getFilterList() = FilterList()

    override fun mangaDetailsRequest(manga: SManga) = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url.substringBefore('#'))
            .build(),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(mangaDetailsRequest(manga))
        .asObservableSuccess()
        .map { response ->
            parseMangaDetails(response).apply { url = manga.url }
        }

    private fun parseMangaDetails(response: Response): SManga {
        val document = response.asJsoup()
        val seriesProps = runCatching {
            document.extractAstroProp<AstroSeriesPropsDto>("postTitle")
        }.getOrNull()

        val title = seriesProps?.postTitle?.takeIf(String::isNotBlank)
            ?: document.selectFirst("h1[itemprop=name]")?.text()
                ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" Manga - Sana scans")
            ?: document.title()

        val description = seriesProps?.postContent
            ?.let { Jsoup.parse(it).text().trim() }
            ?.takeIf(String::isNotEmpty)
            ?: document.selectFirst("[itemprop=description]")?.text()
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

                extractPostContent(document.html())
            }

        val altTitles = seriesProps?.alternativeTitles
            ?.takeIf(String::isNotBlank)
            ?.let { "\n\nAlternative Titles: $it" }
            ?: ""

        val thumbnailUrl = realCoverUrl(document, seriesProps?.featuredImage)

        val genres = seriesProps?.genres
            ?.joinToString { it.name.trim() }
            ?.takeIf(String::isNotBlank)

        val status = seriesProps?.seriesStatus
            ?.let(::parseStatusText)
            ?: parseStatus(document)

        return SManga.create().apply {
            this.title = title
            this.description = (description ?: "") + altTitles
            this.thumbnail_url = thumbnailUrl
            this.genre = genres
            this.status = status
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val seriesSlug = seriesSlug(response.request.url) ?: ""

        return document.select("a[href^=\"/series/$seriesSlug/chapter-\"]")
            .distinctBy { it.attr("href") }
            .mapNotNull { anchor ->
                val isLocked = anchor.selectFirst("svg") != null
                if (isLocked && !preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false)) {
                    return@mapNotNull null
                }

                anchor.toSChapter(isLocked)
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        val pages = document.select("img[data-reader-page-image][src]")
            .map { image -> image.attr("abs:src").ifBlank { image.attr("src") } }

        if (pages.isEmpty()) {
            throw Exception("Unable to find chapter pages")
        }

        val sortedPages = if (sortPagesByFilename) {
            pages.sortedWith(
                compareBy { page ->
                    val filename = page.substringAfterLast('/')
                    val number = Regex("\\d+").find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                    number
                },
            )
        } else {
            pages
        }

        return sortedPages.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.replace(" ", "%20"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = "Show inaccessible chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
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

        val thumbnailUrl = element.selectFirst("img[src]")?.attr("abs:src")

        return SManga.create().apply {
            this.url = slug
            this.title = title
            this.thumbnail_url = thumbnailUrl
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

        return parseStatusText(statusText)
    }

    private fun parseStatusText(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "dropped" -> SManga.CANCELLED
        "onhold", "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
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

private fun org.jsoup.nodes.Element.toSChapter(locked: Boolean): SChapter {
    val href = attr("href")
    val chapterSlug = href.substringAfterLast('/')
    val numberText = Regex("""chapter-([^/?#]+)""")
        .find(chapterSlug)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace('-', '.')
        ?: text().substringAfter("Chapter").trim()

    val cleanedTitle = selectFirst("span")?.text()
        ?.substringAfter("Chapter $numberText")
        ?.trim()
        .orEmpty()
    val chapterTitle = cleanedTitle
        .takeIf { it.isNotEmpty() && it.any { char -> char.isLetterOrDigit() } }
        ?.let { ": $it" }
        ?: ""
    val prefix = if (locked) "🔒 " else ""

    return SChapter.create().apply {
        url = href
        name = "${prefix}Chapter $numberText$chapterTitle"
    }
}

private fun realCoverUrl(document: Document, fallback: String?): String? = document.selectFirst("img[alt^=\"Cover of \"][src]")?.attr("abs:src")
    ?: extractJsonLdImageUrls(document).firstOrNull { it.contains("storage.sanascans.com") }
    ?: fallback
    ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        ?.takeUnless { it.contains("/api/og-image/") }

private fun extractJsonLdImageUrls(document: Document): List<String> {
    val scripts = document.select("script[type=\"application/ld+json\"]")
    if (scripts.isEmpty()) return emptyList()

    return scripts.flatMap { script ->
        val raw = script.data().takeIf(String::isNotEmpty) ?: return@flatMap emptyList()
        val element = runCatching { raw.parseAs<JsonElement>() }.getOrNull() ?: return@flatMap emptyList()
        collectJsonLdImageUrls(element)
    }.distinct()
}

private fun collectJsonLdImageUrls(element: JsonElement): List<String> = when (element) {
    is JsonObject -> {
        val current = listOfNotNull(element["url"]?.asString(), element["@id"]?.asString())
            .filter { it.startsWith("http") && looksLikeImageUrl(it) }
        current + element.values.flatMap(::collectJsonLdImageUrls)
    }

    is JsonArray -> element.flatMap(::collectJsonLdImageUrls)

    else -> emptyList()
}

private inline fun <reified T> Document.extractAstroProp(key: String): T {
    val prop = selectFirst("[props*=$key]")?.attr("props")
        ?: throw Exception("Unable to find prop with $key")
    val json = prop.parseAs<JsonElement>()
    val unwrapped = json.unwrapAstro()

    return unwrapped.parseAs()
}

private inline fun <reified T> Response.extractAstroProp(key: String): T = asJsoup().extractAstroProp(key)

private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
    is JsonArray -> when {
        size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
        else -> JsonArray(map { it.unwrapAstro() })
    }

    is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
    else -> this
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

private fun collectJsonLdDescriptions(element: JsonElement): List<String> = when (element) {
    is JsonObject -> {
        val current = element["description"]?.asString()
        val nested = element.values.flatMap(::collectJsonLdDescriptions)
        if (current == null) nested else listOf(current) + nested
    }

    is JsonArray -> element.flatMap(::collectJsonLdDescriptions)

    else -> emptyList()
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

private fun looksLikeImageUrl(url: String): Boolean = url.endsWith(".jpg", ignoreCase = true) ||
    url.endsWith(".jpeg", ignoreCase = true) ||
    url.endsWith(".png", ignoreCase = true) ||
    url.endsWith(".webp", ignoreCase = true)
private fun Response.asJsoupXml(): Document = Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())

private fun String.toFragmentQueryParameter(name: String): String? {
    val url = "https://localhost/?$this".toHttpUrlOrNull() ?: return null
    return url.queryParameter(name)
}

private fun seriesSlug(url: HttpUrl): String? {
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    val seriesIndex = segments.indexOf("series")
    if (seriesIndex == -1 || seriesIndex + 1 >= segments.size) return null
    return segments[seriesIndex + 1]
}

private const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
