package eu.kanade.tachiyomi.extension.en.onlythebesthentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class OnlyTheBestHentai : HttpSource() {

    override val name = "Only The Best Hentai"
    override val baseUrl = "https://onlythebesthentai.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences by lazy { getPreferences() }

    // Use Z (RFC 822) instead of XXX — Z is available on minSdk 21, XXX requires API 24.
    // The colon in the site's timezone (+00:00) is stripped before parsing so Z matches.
    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
    }

    // ============================= Popular / Latest ===========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/${if (page > 1) "page/$page/" else ""}", headers)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun popularMangaParse(response: Response): MangasPage {
        checkForChallenge(response)
        val doc = response.asJsoup()
        val mangas = doc.select("article.post").map(::elementToManga)
        return MangasPage(mangas, doc.selectFirst("a.next.page-numbers") != null)
    }

    private fun elementToManga(el: Element): SManga = SManga.create().apply {
        val a = el.selectFirst(".blog-entry-title a, .entry-title a")!!
        setUrlWithoutDomain(a.attr("href"))
        title = a.text().replace(TITLE_CLEANUP_REGEX, "").trim()
        thumbnail_url = el.selectFirst(".nv-post-thumbnail-wrap img")?.attr("abs:src")
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ==================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .apply { if (page > 1) addQueryParameter("paged", page.toString()) }
                .build()
            return GET(url, headers)
        }

        val filterUrl = filters.firstNotNullOfOrNull { filter ->
            when (filter) {
                is TagFilter ->
                    tagList.getOrNull(filter.state - 1)?.let { "$baseUrl/tag/${it.slug}/" }
                is ParodyFilter ->
                    parodyList.getOrNull(filter.state - 1)?.let { "$baseUrl/parody/${it.slug}/" }
                is CharacterFilter ->
                    characterList.getOrNull(filter.state - 1)?.let { "$baseUrl/characters/${it.slug}/" }
                is ArtistFilter ->
                    artistList.getOrNull(filter.state - 1)?.let { "$baseUrl/artist/${it.slug}/" }
                else -> null
            }
        } ?: "$baseUrl/"

        return GET("$filterUrl${if (page > 1) "page/$page/" else ""}", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================= Manga Details =============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.selectFirst("h1.manga-title")!!.text()
            thumbnail_url = doc.selectFirst(".manga-box .manga-img img")?.attr("abs:src")
            genre = doc.select(
                ".manga-tags-container:has(.manga-tags-label:containsOwn(Tags)) .tag-button",
            ).joinToString { el: Element -> el.text() }
            author = doc.select(
                ".manga-tags-container:has(.manga-tags-label:containsOwn(Artist)) .tag-button",
            ).joinToString { el: Element -> el.text() }
            description = buildDescription(doc)
            status = SManga.COMPLETED
        }
    }

    private fun buildDescription(doc: Document): String = buildString {
        val parodies = doc.select(
            ".manga-tags-container:has(.manga-tags-label:containsOwn(Parody)) .tag-button",
        ).map { el: Element -> el.text() }
        if (parodies.isNotEmpty()) appendLine("Parody: ${parodies.joinToString()}")

        val characters = doc.select(
            ".manga-tags-container:has(.manga-tags-label:containsOwn(Characters)) .tag-button",
        ).map { el: Element -> el.text() }
        if (characters.isNotEmpty()) appendLine("Characters: ${characters.joinToString()}")

        val pages = doc.select(".manga-tags-container:has(.manga-tags-label:containsOwn(Pages))")
            .firstOrNull()?.text()?.replace(Regex("[^0-9]"), "")
        if (!pages.isNullOrEmpty()) appendLine("Pages: $pages")

        val body = doc.selectFirst(".manga-info p")
            ?.text()?.removePrefix("Description:")?.trim()
        if (!body.isNullOrEmpty()) {
            if (isNotEmpty()) appendLine()
            append(body)
        }
    }.trim()

    // ============================== Chapters =================================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()

        val pageCount = doc.select(".manga-tags-container").firstNotNullOfOrNull { container: Element ->
            val label = container.selectFirst(".manga-tags-label")?.text()
                ?: return@firstNotNullOfOrNull null
            if (!label.startsWith("Pages")) return@firstNotNullOfOrNull null
            container.text().replace(Regex("[^0-9]"), "").toIntOrNull()
        }

        // Convert +HH:MM to +HHMM so Z (RFC 822) can parse it on API 21
        val rawDate = doc.selectFirst("meta[property=article:published_time]")
            ?.attr("content")
            ?.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            ?: ""

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = if (pageCount != null) "Chapter [$pageCount pages]" else "Chapter"
                chapter_number = 1f
                date_upload = dateFormat.tryParse(rawDate)
            },
        )
    }

    // ============================== Page List ================================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".manga-gallery-wrapper figure.wp-block-image img")
        .mapIndexed { i: Int, img: Element -> Page(i, imageUrl = bestImageUrl(img)) }

    private fun bestImageUrl(img: Element): String {
        val srcset = img.attr("srcset")
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").map { it.trim() }
                .maxByOrNull { entry: String ->
                    entry.split(Regex("\\s+")).lastOrNull()?.removeSuffix("w")?.toIntOrNull() ?: 0
                }
            val url = best?.split(Regex("\\s+"))?.firstOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return img.attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ======================== Challenge Detection ============================

    private fun checkForChallenge(response: Response) {
        val peek = response.peekBody(512).string()
        if (peek.contains("One moment, please") || peek.contains("wsidchk")) {
            throw Exception(
                "Bot protection detected. Open this source in WebView to solve the challenge, " +
                    "then return to Mihon.",
            )
        }
    }

    // ============================== Filters ==================================

    private data class FilterEntry(val name: String, val slug: String, val count: Int) {
        override fun toString() = "$name ($count)"
    }

    /** DTO matching the WP REST API taxonomy response shape. */
    private data class TaxonomyDto(val name: String, val slug: String, val count: Int) {
        fun toFilterEntry() = FilterEntry(name, slug, count)

        companion object {
            fun fromJson(obj: JSONObject) = TaxonomyDto(
                name = obj.getString("name"),
                slug = obj.getString("slug"),
                count = obj.optInt("count", 0),
            )
        }
    }

    private var tagList: List<FilterEntry> = emptyList()
    private var parodyList: List<FilterEntry> = emptyList()
    private var characterList: List<FilterEntry> = emptyList()
    private var artistList: List<FilterEntry> = emptyList()
    private var filtersLoaded = false
    private var filterFetchInProgress = false

    // ----------------------- REST API fetch ---------------------------------

    private fun fetchTaxonomy(restPath: String): List<FilterEntry> {
        val result = mutableListOf<FilterEntry>()
        var page = 1
        var totalPages = 1

        do {
            val response = client.newCall(
                GET("$baseUrl/wp-json/wp/v2/$restPath?per_page=100&page=$page", headers),
            ).execute()
            if (page == 1) {
                totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            }
            val arr = JSONArray(response.body.string())
            result += (0 until arr.length()).map { i: Int ->
                TaxonomyDto.fromJson(arr.getJSONObject(i)).toFilterEntry()
            }
            page++
        } while (page <= totalPages)

        return result.sortedBy { it.name.lowercase() }
    }

    // ----------------------- Cache ------------------------------------------

    private fun isCacheValid(): Boolean = System.currentTimeMillis() - preferences.getLong(PREF_TIMESTAMP, 0L) < TimeUnit.DAYS.toMillis(1)

    private fun serialize(entries: List<FilterEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("n", e.name)
                    put("s", e.slug)
                    put("c", e.count)
                },
            )
        }
        return arr.toString()
    }

    private fun deserialize(json: String?): List<FilterEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i: Int ->
                val obj = arr.getJSONObject(i)
                FilterEntry(
                    name = obj.optString("n", obj.optString("name")),
                    slug = obj.optString("s", obj.optString("slug")),
                    count = obj.optInt("c", obj.optInt("count", 0)),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadFiltersFromCache(): Boolean {
        if (!isCacheValid()) return false
        val tags = deserialize(preferences.getString(PREF_TAGS, null))
        if (tags.isEmpty()) return false
        tagList = tags
        parodyList = deserialize(preferences.getString(PREF_PARODIES, null))
        characterList = deserialize(preferences.getString(PREF_CHARACTERS, null))
        artistList = deserialize(preferences.getString(PREF_ARTISTS, null))
        return true
    }

    private fun triggerFilterLoad() {
        if (filtersLoaded || filterFetchInProgress) return
        if (loadFiltersFromCache()) {
            filtersLoaded = true
            return
        }

        filterFetchInProgress = true
        Thread {
            try {
                tagList = fetchTaxonomy("tags")
                parodyList = fetchTaxonomy("categories")
                characterList = fetchTaxonomy("characters")
                artistList = fetchTaxonomy("artist")
                preferences.edit()
                    .putString(PREF_TAGS, serialize(tagList))
                    .putString(PREF_PARODIES, serialize(parodyList))
                    .putString(PREF_CHARACTERS, serialize(characterList))
                    .putString(PREF_ARTISTS, serialize(artistList))
                    .putLong(PREF_TIMESTAMP, System.currentTimeMillis())
                    .apply()
                filtersLoaded = true
            } catch (_: Exception) {
            } finally {
                filterFetchInProgress = false
            }
        }.start()
    }

    // ----------------------- Filter classes ---------------------------------

    private inner class TagFilter :
        Filter.Select<String>(
            "Tag",
            (listOf("Any") + tagList.map { it.toString() }).toTypedArray(),
        )

    private inner class ParodyFilter :
        Filter.Select<String>(
            "Parody",
            (listOf("Any") + parodyList.map { it.toString() }).toTypedArray(),
        )

    private inner class CharacterFilter :
        Filter.Select<String>(
            "Character",
            (listOf("Any") + characterList.map { it.toString() }).toTypedArray(),
        )

    private inner class ArtistFilter :
        Filter.Select<String>(
            "Artist",
            (listOf("Any") + artistList.map { it.toString() }).toTypedArray(),
        )

    override fun getFilterList(): FilterList {
        triggerFilterLoad()
        return if (!filtersLoaded) {
            FilterList(
                Filter.Header("⚠ Press ↺ Reset to load filters"),
                Filter.Header("Filters load in a few seconds — press Reset again"),
            )
        } else {
            FilterList(
                Filter.Header("Only one filter applies at a time (first selected wins)"),
                TagFilter(),
                ParodyFilter(),
                CharacterFilter(),
                ArtistFilter(),
            )
        }
    }

    companion object {
        private val TITLE_CLEANUP_REGEX = Regex("""\s*\[\d+]\s*$""")
        private const val PREF_TAGS = "filter_tags"
        private const val PREF_PARODIES = "filter_parodies"
        private const val PREF_CHARACTERS = "filter_characters"
        private const val PREF_ARTISTS = "filter_artists"
        private const val PREF_TIMESTAMP = "filter_cache_ts"
    }
}
