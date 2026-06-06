package eu.kanade.tachiyomi.extension.en.divascans

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
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class DivaScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Diva Scans"
    override val baseUrl = "https://divascans.org"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy { getPreferences() }
    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
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
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        for (filter in filters) {
            when (filter) {
                is SortFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("sort", filter.selected)
                is StatusFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("status", filter.selected)
                is OriginFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("origin", filter.selected)
                is GenreFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("genre", it.value) }
                is TagFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("tag", it.value) }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun searchMangaParse(response: Response): MangasPage = parseJsonMangaList(response)

    private fun parseJsonMangaList(response: Response): MangasPage {
        val jsonString = response.body.string()
        val jsonElement = runCatching { json.parseToJsonElement(jsonString) }.getOrNull() ?: return MangasPage(emptyList(), false)
        val jsonArray = findJsonArray(jsonElement) ?: return MangasPage(emptyList(), false)

        val savedGenres = preferences.getStringSet(GENRES_PREF_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val savedTags = preferences.getStringSet(TAGS_PREF_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        var prefsChanged = false

        val mangas = jsonArray.mapNotNull { item ->
            val obj = item.jsonObject

            val genresArr = obj["genres"]?.jsonArray
            if (genresArr != null) {
                for (genreObj in genresArr) {
                    val genreSlug = genreObj.jsonObject["genre"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull
                    if (!genreSlug.isNullOrEmpty() && savedGenres.add(formatSlug(genreSlug))) prefsChanged = true
                }
            }

            val tagsArr = obj["tags"]?.jsonArray
            if (tagsArr != null) {
                for (tagObj in tagsArr) {
                    val tagSlug = tagObj.jsonObject["tag"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull
                        ?: tagObj.jsonObject["slug"]?.jsonPrimitive?.contentOrNull
                    if (!tagSlug.isNullOrEmpty() && savedTags.add(formatSlug(tagSlug))) prefsChanged = true
                }
            }

            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val slug = obj["urlSlug"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: obj["slug"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

            var cover = obj["coverImage"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""

            if (cover.isBlank() && obj.containsKey("cover")) {
                val coverElement = obj["cover"]
                cover = if (coverElement is JsonObject) {
                    coverElement["url"]?.jsonPrimitive?.contentOrNull ?: ""
                } else {
                    coverElement?.jsonPrimitive?.contentOrNull ?: ""
                }
            }

            if (title.isEmpty() || slug.isEmpty()) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                this.url = "/series/comic/$slug"
                this.thumbnail_url = cleanImageUrl(cover)
            }
        }

        if (prefsChanged) {
            preferences.edit()
                .putStringSet(GENRES_PREF_KEY, savedGenres)
                .putStringSet(TAGS_PREF_KEY, savedTags)
                .apply()
        }

        return MangasPage(mangas, mangas.size >= 10)
    }

    private fun formatSlug(slug: String): String = slug.replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun findJsonArray(element: JsonElement): JsonArray? {
        if (element is JsonArray) return element
        if (element is JsonObject) {
            val keys = listOf("data", "series", "items", "results", "mangas")
            for (key in keys) {
                if (element.containsKey(key) && element[key] is JsonArray) {
                    return element[key]?.jsonArray
                }
            }
        }
        return null
    }

    private fun getHydratedDocument(html: String): Document {
        val doc = Jsoup.parse(html)
        if (doc.select("a[href*='/chapter/']").size > 1) return doc

        val sb = StringBuilder()
        val rscRegex = Regex("""self\.__next_f\.push\(\[\d+,\s*"((?:[^"\\]|\\.)*)"\]\)""")

        rscRegex.findAll(html).forEach { match ->
            try {
                val jsonPayload = """{"d": "${match.groupValues[1]}"}"""
                val unescapedStr = json.parseToJsonElement(jsonPayload).jsonObject["d"]?.jsonPrimitive?.contentOrNull
                if (!unescapedStr.isNullOrEmpty()) {
                    sb.append(unescapedStr)
                }
            } catch (_: Exception) {}
        }

        if (sb.isNotEmpty()) doc.body().append(sb.toString())
        return doc
    }

    // --- HTML Details Layer ---

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.body.string()
        val doc = getHydratedDocument(html)
        val manga = SManga.create()

        val fullContent = doc.body().html()

        // 1. JSON Attempt (Hydrated)
        val seriesRegex = Regex("""(?s)\\?"series\\?"\s*:\s*(\{(?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*\})""")
        val match = seriesRegex.find(fullContent)
        if (match != null) {
            runCatching {
                val jsonStr = match.groupValues[1].replace("\\\"", "\"")
                val mangaObj = json.parseToJsonElement(jsonStr).jsonObject

                manga.title = mangaObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                manga.thumbnail_url = cleanImageUrl(mangaObj["coverImage"]?.jsonPrimitive?.contentOrNull ?: "")
                manga.description = mangaObj["description"]?.jsonPrimitive?.contentOrNull
                manga.author = mangaObj["author"]?.jsonPrimitive?.contentOrNull
                manga.artist = mangaObj["artist"]?.jsonPrimitive?.contentOrNull ?: manga.author

                val statusStr = mangaObj["status"]?.jsonPrimitive?.contentOrNull ?: ""
                manga.status = when {
                    statusStr.contains("Ongoing", true) -> SManga.ONGOING
                    statusStr.contains("Completed", true) -> SManga.COMPLETED
                    statusStr.contains("Hiatus", true) -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }

                val origin = mangaObj["origin"]?.jsonPrimitive?.contentOrNull?.let { formatSlug(it) }
                val genresList = (mangaObj["genres"] ?: mangaObj["genre"])?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: it.jsonObject["genre"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                }
                val tagsList = mangaObj["tags"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: it.jsonObject["tag"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                }

                manga.genre = listOfNotNull(origin).plus(genresList ?: emptyList()).plus(tagsList ?: emptyList()).joinToString()

                if (!manga.description.isNullOrBlank()) return manga
            }
        }

        // 2. DOM Fallback
        manga.title = doc.select("h1").text().ifBlank { doc.title() }
        manga.thumbnail_url = cleanImageUrl(doc.select("img[src*='cover'], img[src*='thumbnail']").attr("src"))
        manga.description = doc.select("div:containsOwn(Synopsis), div:containsOwn(Description), div:containsOwn(synopsis)").firstOrNull()?.nextElementSibling()?.text()
            ?: doc.select("main p").firstOrNull()?.text()

        manga.author = doc.select("span:containsOwn(Author)").firstOrNull()?.nextElementSibling()?.text()
            ?: doc.select("a[href*='/team/']").firstOrNull()?.text()

        val statusText = doc.select("span:containsOwn(Status)").firstOrNull()?.nextElementSibling()?.text()
            ?: doc.select("div:containsOwn(Status) + div").text()
        manga.status = when {
            statusText.contains("Ongoing", true) -> SManga.ONGOING
            statusText.contains("Completed", true) -> SManga.COMPLETED
            statusText.contains("Hiatus", true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val originText = doc.select("span:containsOwn(Origin)").firstOrNull()?.nextElementSibling()?.text()
            ?: doc.select("div:containsOwn(Origin) + a").text()

        val genres = doc.select("a[href*='genre=']").map { it.text() }
        val tags = doc.select("a[href*='tag=']").map { it.text() }

        manga.genre = listOfNotNull(originText.takeIf { it.isNotBlank() }).plus(genres).plus(tags).joinToString()

        return manga
    }



    // --- HTML Chapter Layer ---

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        val showPaid = preferences.getBoolean(SHOW_PAID_CHAPTERS_PREF, false)
        val slug = response.request.url.pathSegments.last()

        // Hydrate to unescape RSC payloads which contain the chapter data
        val doc = getHydratedDocument(html)
        val fullContent = doc.body().html()

        // 1. Improved JSON extraction from HYDRATED content
        val chaptersArrays = mutableListOf<JsonArray>()
        // Match both "chapters":[...] and \"chapters\":[...]
        val chapterJsonRegex = Regex("""(?s)\\?"chapters\\?"\s*:\s*(\[.*?\])""")

        chapterJsonRegex.findAll(fullContent).forEach { match ->
            runCatching {
                val element = json.parseToJsonElement(match.groupValues[1])
                if (element is JsonArray) chaptersArrays.add(element)
            }
        }

        val chaptersArray = chaptersArrays.maxByOrNull { it.size }
        if (!chaptersArray.isNullOrEmpty()) {
            val chapters = chaptersArray.mapNotNull { element ->
                val chap = element.jsonObject
                val isLocked = (chap["isLocked"]?.jsonPrimitive?.booleanOrNull ?: false) ||
                    (chap["coinPrice"]?.jsonPrimitive?.intOrNull ?: 0) > 0
                val coinPrice = chap["coinPrice"]?.jsonPrimitive?.intOrNull ?: 0

                if (isLocked && !showPaid) return@mapNotNull null

                val numStr = chap["number"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                SChapter.create().apply {
                    // Use number in URL as requested by site structure
                    url = "/series/comic/$slug/chapter/$numStr"

                    val prefix = if (isLocked) "\uD83D\uDD12 " else ""
                    val suffix = if (isLocked && coinPrice > 0) " ($coinPrice coins)" else ""
                    val baseName = chap["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() } ?: "Chapter $numStr"

                    name = "$prefix$baseName$suffix"
                    chapter_number = numStr.toFloatOrNull() ?: 0f
                }
            }
            if (chapters.size > 1) return chapters.sortedByDescending { it.chapter_number }
        }

        // 2. Fallback: Parse via DOM from hydrated document
        val chapterElements = doc.select("a[href*='/chapter/']")
        if (chapterElements.isNotEmpty()) {
            return chapterElements.mapNotNull { element ->
                val nameText = element.text()

                // Filter out navigation/action buttons
                if (nameText.contains("Start Reading", true) || nameText.contains("Continue Reading", true)) return@mapNotNull null

                val href = element.attr("href")
                val chapterUrl = when {
                    href.startsWith("http") -> href.substringAfter(baseUrl)
                    href.startsWith("/") -> href
                    else -> return@mapNotNull null
                }

                if (!chapterUrl.contains("/chapter/")) return@mapNotNull null

                SChapter.create().apply {
                    url = chapterUrl
                    name = nameText
                    chapter_number = nameText.trim().substringAfter("Chapter").trim().takeWhile { it.isDigit() || it == '.' }.toFloatOrNull()
                        ?: nameText.filter { it.isDigit() || it == '.' }.toFloatOrNull()
                        ?: 0f
                }
            }.filter { it.name.contains("Chapter", true) || it.chapter_number > 0 }
                .distinctBy { it.url }
                .sortedByDescending { it.chapter_number }
        }

        return emptyList()
    }

    // --- Page Reader Layer ---

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
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

        // 1. Decode proxy URLs
        var cleanUrl = if (url.contains("url=")) {
            val encodedPart = url.substringAfter("url=").substringBefore("&")
            java.net.URLDecoder.decode(encodedPart, "UTF-8")
        } else {
            url
        }

        // 2. Ensure base URL
        if (cleanUrl.startsWith("/")) {
            cleanUrl = "$baseUrl$cleanUrl"
        }

        // 3. Force CDN and strip invalid next/image paths
        return cleanUrl.replace("divascans.org", "media.divascans.org")
            .replace("media.media.divascans.org", "media.divascans.org")
            .replace("/_next/image?url=", "")
            .replace("/uploads/", "/")
            .substringBefore("&") // Strip all Next.js parameters
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "show_paid_chapters"
        private const val GENRES_PREF_KEY = "saved_genres"
        private const val TAGS_PREF_KEY = "saved_tags"
    }
}
