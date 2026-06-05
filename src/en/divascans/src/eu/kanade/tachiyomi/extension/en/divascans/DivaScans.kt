package eu.kanade.tachiyomi.extension.en.divascans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class DivaScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Diva Scans"
    override val baseUrl = "https://divascans.org"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    // Cloudflare Bypass Headers
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("Referer", "$baseUrl/")

    // --- Configuration (Settings Menu) ---

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val paidChapterPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = "Show Paid Chapters"
            summary = "Show chapters that require coins to read. Note: You cannot read these natively in the app."
            setDefaultValue(false)
        }
        screen.addPreference(paidChapterPref)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        OriginFilter(),
        GenreFilter(),
        TagFilter(),
    )

    // --- Direct API Catalog & Search Layer ---

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If the user typed a name in the search bar, use the text search API
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        // If the user is using the filter menu, use the series API
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        for (filter in filters) {
            when (filter) {
                is SortFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("sort", filter.selected)
                is StatusFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("status", filter.selected)
                is OriginFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("origin", filter.selected)

                // Multi-Select Logic for Genres
                is GenreFilter -> {
                    val selectedGenres = filter.state.filter { it.state }.map { it.value }
                    if (selectedGenres.isNotEmpty()) {
                        // This adds a parameter for every checked box (e.g., &genre=action&genre=comedy)
                        selectedGenres.forEach { url.addQueryParameter("genre", it) }
                    }
                }

                // Multi-Select Logic for Tags
                is TagFilter -> {
                    val selectedTags = filter.state.filter { it.state }.map { it.value }
                    if (selectedTags.isNotEmpty()) {
                        selectedTags.forEach { url.addQueryParameter("tag", it) }
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun searchMangaParse(response: Response): MangasPage = parseJsonMangaList(response)

    private fun parseJsonMangaList(response: Response): MangasPage {
        val jsonString = response.body!!.string()

        val jsonObj = runCatching { JSONObject(jsonString) }.getOrNull()
        val jsonArray = jsonObj?.let { findJsonArray(it) }
            ?: runCatching { JSONArray(jsonString) }.getOrDefault(JSONArray())

        val mangas = (0 until jsonArray.length()).mapNotNull { i ->
            val item = jsonArray.optJSONObject(i) ?: return@mapNotNull null

            val title = item.optString("title").ifEmpty { item.optString("name") }
            val slug = item.optString("urlSlug").ifEmpty { item.optString("slug") }.ifEmpty { item.optString("id") }
            var cover = item.optString("coverImage").ifEmpty { item.optString("cover") }.ifEmpty { item.optString("thumbnail") }

            if (title.isEmpty() || slug.isEmpty()) return@mapNotNull null
            if (cover.isEmpty() && item.has("cover")) {
                cover = item.optJSONObject("cover")?.optString("url") ?: ""
            }

            SManga.create().apply {
                this.title = title
                this.url = "/series/comic/$slug"
                this.thumbnail_url = cleanImageUrl(cover)
            }
        }
        return MangasPage(mangas, mangas.size >= 10)
    }

    private fun findJsonArray(obj: JSONObject): JSONArray? {
        val keys = listOf("data", "series", "items", "results", "mangas")
        for (key in keys) {
            if (obj.has(key) && obj.optJSONArray(key) != null) return obj.getJSONArray(key)
        }
        obj.keys().forEach { key ->
            val target = obj.optJSONArray(key)
            if (target != null) return target
        }
        return null
    }

    // --- Next.js React Component DOM Reconstruction Layer ---

    private fun getHydratedDocument(html: String): Document {
        val doc = Jsoup.parse(html)
        if (doc.select("a[href*='/chapter/']").size > 1) return doc

        val sb = StringBuilder()
        val rscRegex = Regex("""self\.__next_f\.push\(\[\d+,\s*("(?:[^"\\]|\\.)*")\]\)""")
        rscRegex.findAll(html).forEach { match ->
            try {
                val jsonPayload = """{"data": ${match.groupValues[1]}}"""
                val unescapedStr = JSONObject(jsonPayload).getString("data")
                sb.append(unescapedStr)
            } catch (e: Exception) {}
        }

        if (sb.isNotEmpty()) {
            doc.body().append(sb.toString())
        }
        return doc
    }

    // --- HTML Details Layer ---

    override fun mangaDetailsParse(response: Response): SManga {
        val unescapedHtml = getHydratedDocument(response.body!!.string())
        val document = Jsoup.parse(unescapedHtml.html(), response.request.url.toString())
        document.select("noscript, script, style").remove()

        return SManga.create().apply {
            title = document.select("main h1, div h1, h1").firstOrNull { !it.text().contains("JavaScript", true) }?.text()?.trim() ?: ""
            artist = document.select("div:contains(Artist) + div, span:contains(Artist) + span, p:contains(Artist)").first()?.text()
            author = document.select("div:contains(Author) + div, span:contains(Author) + span, p:contains(Author)").first()?.text() ?: artist
            description = document.select("div.synopsis, div:contains(Synopsis), p.description, div.description").text().trim()

            val statusStr = document.select("div:contains(Status), span:contains(Status)").text()
            status = when {
                statusStr.contains("Ongoing", true) -> SManga.ONGOING
                statusStr.contains("Completed", true) -> SManga.COMPLETED
                statusStr.contains("Hiatus", true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val imgElement = document.select("main img, div img[src*='cover']").first()
            thumbnail_url = cleanImageUrl(imgElement?.attr("src") ?: "")
        }
    }

    // --- Chapter Layer ---

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body!!.string()
        val document = getHydratedDocument(html)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val showPaid = preferences.getBoolean(SHOW_PAID_CHAPTERS_PREF, false)

        val domChapters = document.select("a[href*='/chapter/']").filter { element ->
            element.select("span.font-medium").isNotEmpty() &&
                !element.text().contains("Start Reading", true) &&
                !element.text().contains("Read First", true)
        }

        if (domChapters.isNotEmpty()) {
            val chapters = mutableListOf<SChapter>()
            for (element in domChapters) {
                val isLocked = element.select("svg.lucide-lock").isNotEmpty()

                if (isLocked && !showPaid) continue

                val chapter = SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("href"))

                    val titleSpan = element.select("span.font-medium").first()
                    val rawName = titleSpan?.text()?.replace("NEW", "", true)?.trim() ?: element.text().trim()
                    val price = element.select("svg.lucide-coins").first()?.parent()?.text()?.trim()

                    name = if (isLocked) {
                        if (!price.isNullOrEmpty()) "\uD83D\uDD12 $rawName - $price Coins" else "\uD83D\uDD12 $rawName"
                    } else {
                        rawName
                    }

                    val dateText = element.select("svg.lucide-clock ~ span, span.whitespace-nowrap").text().trim()
                    date_upload = runCatching { dateFormat.parse(dateText)?.time ?: 0L }.getOrDefault(0L)
                }
                chapters.add(chapter)
            }
            return chapters.distinctBy { it.url }.reversed()
        }

        val chapterRegex = Regex("""(/series/comic/[^/]+/chapter/[^"\\}]+)""")
        val urls = chapterRegex.findAll(document.html()).map { it.groupValues[1] }.toList()

        return urls.distinct().map { url ->
            SChapter.create().apply {
                this.url = url
                val chapNum = url.substringAfterLast("/")
                this.name = "Chapter $chapNum"
            }
        }.reversed()
    }

    // --- Page Reader Layer ---

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body!!.string()
        val document = getHydratedDocument(html)

        val domImages = document.select("div.reader-images img, div.chapter-container img, main img[src*='chapter']")
        if (domImages.isNotEmpty()) {
            return domImages.mapIndexed { index, element ->
                val imgUrl = element.attr("data-src").ifEmpty { element.attr("src") }
                Page(index, "", cleanImageUrl(imgUrl))
            }
        }

        val imgRegex = Regex("""(https?://[^"'\\]+\.(?:jpg|jpeg|png|webp))""")
        val urls = imgRegex.findAll(document.html()).map { it.groupValues[1] }.distinct().toList()

        val pageUrls = urls.filter { it.contains("/chapters/", true) || it.contains("/pages/", true) }
        if (pageUrls.isNotEmpty()) {
            return pageUrls.mapIndexed { index, url -> Page(index, "", cleanImageUrl(url)) }
        }

        return urls.mapIndexed { index, url -> Page(index, "", cleanImageUrl(url)) }
    }

    // --- Utilities ---

    private fun cleanImageUrl(url: String): String {
        if (url.isEmpty()) return ""
        var cleanUrl = url.replace("&amp;", "&")

        try {
            if (cleanUrl.contains("url=")) {
                val encodedPart = cleanUrl.substringAfter("url=").substringBefore("&")
                val decoded = java.net.URLDecoder.decode(encodedPart, "UTF-8")
                if (decoded.startsWith("http")) {
                    cleanUrl = decoded
                }
            }
        } catch (_: Exception) {}

        if (cleanUrl.startsWith("/")) {
            cleanUrl = "$baseUrl$cleanUrl"
        }

        cleanUrl = cleanUrl.replace("divascans.org", "media.divascans.org")
            .replace("media.media.divascans.org", "media.divascans.org")
            .replace("/uploads/", "/")

        return cleanUrl
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "show_paid_chapters"
    }
}
