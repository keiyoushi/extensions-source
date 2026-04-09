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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
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

    private val preferences by getPreferencesLazy()

    private val challengeInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val peek = response.peekBody(512).string()
        if (peek.contains("One moment, please") || peek.contains("wsidchk")) {
            throw Exception(
                "Bot protection detected. Open this source in WebView to solve the challenge, " +
                    "then return to Mihon.",
            )
        }
        response
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(challengeInterceptor)
        .build()

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
    }

    // ============================= Popular / Latest ===========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/${if (page > 1) "page/$page/" else ""}", headers)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("article.post").map(::elementToManga)
        return MangasPage(mangas, doc.selectFirst("a.next.page-numbers") != null)
    }

    private fun elementToManga(el: Element): SManga = SManga.create().apply {
        val a = el.selectFirst(".blog-entry-title a, .entry-title a")!!
        setUrlWithoutDomain(a.absUrl("href"))
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
            .firstOrNull()?.text()?.replace(NON_DIGIT_REGEX, "")
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
            container.text().replace(NON_DIGIT_REGEX, "").toIntOrNull()
        }

        val rawDate = doc.selectFirst("meta[property=article:published_time]")
            ?.attr("content")
            ?.replace(TIMEZONE_COLON_REGEX, "$1$2")
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

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".manga-gallery-wrapper figure.wp-block-image img")
        .mapIndexed { i: Int, img: Element -> Page(i, imageUrl = bestImageUrl(img)) }

    private fun bestImageUrl(img: Element): String {
        val srcset = img.attr("srcset")
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").map { it.trim() }
                .maxByOrNull { entry: String ->
                    entry.split(WHITESPACE_REGEX).lastOrNull()?.removeSuffix("w")?.toIntOrNull() ?: 0
                }
            val url = best?.split(WHITESPACE_REGEX)?.firstOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return img.attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==================================

    @Serializable
    private data class FilterEntry(val name: String, val slug: String, val count: Int) {
        override fun toString() = "$name ($count)"
    }

    @Serializable
    private data class TaxonomyDto(val name: String, val slug: String, val count: Int = 0) {
        fun toFilterEntry() = FilterEntry(name, slug, count)
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
            result += response.parseAs<List<TaxonomyDto>>().map { it.toFilterEntry() }
            page++
        } while (page <= totalPages)

        return result.sortedBy { it.name.lowercase() }
    }

    // ----------------------- Cache ------------------------------------------

    private fun isCacheValid(): Boolean = System.currentTimeMillis() - preferences.getLong(PREF_TIMESTAMP, 0L) < TimeUnit.DAYS.toMillis(1)

    private fun loadFiltersFromCache(): Boolean {
        if (!isCacheValid()) return false
        val tags = preferences.getString(PREF_TAGS, null)
            ?.let { runCatching { Json.decodeFromString<List<FilterEntry>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: return false
        tagList = tags
        parodyList = preferences.getString(PREF_PARODIES, null)
            ?.let { runCatching { Json.decodeFromString<List<FilterEntry>>(it) }.getOrNull() }
            ?: emptyList()
        characterList = preferences.getString(PREF_CHARACTERS, null)
            ?.let { runCatching { Json.decodeFromString<List<FilterEntry>>(it) }.getOrNull() }
            ?: emptyList()
        artistList = preferences.getString(PREF_ARTISTS, null)
            ?.let { runCatching { Json.decodeFromString<List<FilterEntry>>(it) }.getOrNull() }
            ?: emptyList()
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
                    .putString(PREF_TAGS, tagList.toJsonString())
                    .putString(PREF_PARODIES, parodyList.toJsonString())
                    .putString(PREF_CHARACTERS, characterList.toJsonString())
                    .putString(PREF_ARTISTS, artistList.toJsonString())
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
        private val NON_DIGIT_REGEX = Regex("[^0-9]")
        private val TIMEZONE_COLON_REGEX = Regex("([+-]\\d{2}):(\\d{2})$")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private const val PREF_TAGS = "filter_tags"
        private const val PREF_PARODIES = "filter_parodies"
        private const val PREF_CHARACTERS = "filter_characters"
        private const val PREF_ARTISTS = "filter_artists"
        private const val PREF_TIMESTAMP = "filter_cache_ts"
    }
}
