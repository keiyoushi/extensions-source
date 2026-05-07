package eu.kanade.tachiyomi.extension.en.myhentaigallery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class MyHentaiGallery : HttpSource() {

    override val name = "MyHentaiGallery"
    override val baseUrl = "https://myhentaigallery.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // =============================== Popular ================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/views/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/gpage/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Search =================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(GET("$baseUrl/g/$id", headers))
            .asObservableSuccess()
            .map { response ->
                val details = mangaDetailsParse(response).apply { url = "/g/$id" }
                MangasPage(listOf(details), false)
            }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(page.toString())
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.firstInstanceOrNull<GenreFilter>()
        val sortFilter = filterList.firstInstanceOrNull<SortFilter>()
        val tagLookupFilter = filterList
            .filterIsInstance<TagLookupFilter>()
            .firstOrNull { it.state.isNotBlank() }

        if (tagLookupFilter != null) {
            val tagId = tagLookupFilter.resolveTagId()
            return GET("$baseUrl/a/${tagLookupFilter.uriPart}/$tagId/$page", headers)
        }

        if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
            val catId = categoryFilter.toUriPart()
            return GET("$baseUrl/g/category/$catId/$page", headers)
        }

        val sortPath = sortFilter?.toUriPart() ?: "gpage"
        return GET("$baseUrl/$sortPath/$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseComicListing(response)

    // ============================== Filters =================================

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
        Filter.Separator(),
        Filter.Header("Use one category/artist/parody filter at a time"),
        Filter.Header("Artists/Parodies accept ID, tag URL, or exact name"),
        ArtistFilter(),
        ParodyFilter(),
    )

    // =========================== Comic Listing ==============================

    private fun parseComicListing(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.comic-inner").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h2")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.encodeSpaces()
            }
        }

        val hasNextPage = document.selectFirst("li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ==============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info: Element = document.selectFirst("div.comic-header")!!
        val categories = info.select("div:containsOwn(categories) a").eachText()
        val artists = info.select("div:containsOwn(artists) a").eachText()
        val parodies = info.select("div:containsOwn(parodies) a").eachText()

        return SManga.create().apply {
            title = info.selectFirst("h1")!!.text()
            genre = (categories + artists + parodies).joinToString()
            artist = artists.joinToString()
            thumbnail_url = document.selectFirst(".comic-listing .comic-inner img")?.absUrl("src")?.encodeSpaces()
            status = SManga.COMPLETED
            initialized = true
            description = buildString {
                info.select("div:containsOwn(groups) a")
                    .takeIf { it.isNotEmpty() }
                    ?.also { if (isNotEmpty()) append("\n\n") }
                    ?.also { appendLine("Groups:") }
                    ?.joinToString("\n") { "- ${it.text()}" }
                    ?.also { append(it) }

                info.select("div:containsOwn(parodies) a")
                    .takeIf { it.isNotEmpty() }
                    ?.also { if (isNotEmpty()) append("\n\n") }
                    ?.also { appendLine("Parodies:") }
                    ?.joinToString("\n") { "- ${it.text()}" }
                    ?.also { append(it) }
            }
        }
    }

    // =========================== Chapter List ===============================

    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            url = response.request.url.toString().substringAfter(baseUrl)
        },
    )

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ============================== Page List ===============================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("div.comic-thumb img[src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src").replace("/thumbnail/", "/original/").encodeSpaces())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    private fun TagLookupFilter.resolveTagId(): String {
        val value = state.trim()
        value.toLongOrNull()?.let { return it.toString() }

        TAG_URL_REGEX.find(value)?.let { match ->
            val namespace = match.groupValues[1].lowercase()
            if (namespace != uriPart) {
                throw Exception("Expected a $uriPart URL, got a $namespace URL")
            }

            return match.groupValues[2]
        }

        return lookupTagId(uriPart, value)
            ?: throw Exception("No $uriPart \"$value\" was found. Use the exact tag name, numeric ID, or full MyHentaiGallery tag URL.")
    }

    private fun lookupTagId(uriPart: String, name: String): String? {
        val lookup = tagLookupCache.getOrPut(uriPart) { loadTagLookup(uriPart) }
        return lookup[name.normalizeTagName()]
    }

    private fun loadTagLookup(uriPart: String): Map<String, String> {
        val tagUrlRegex = Regex("""/$uriPart/(\d+)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

        return client.newCall(GET("$baseUrl/tag/$uriPart", headers)).execute().use { response ->
            response.asJsoup()
                .select("a[href*='/$uriPart/']")
                .mapNotNull { element ->
                    val id = tagUrlRegex.find(element.attr("href"))?.groupValues?.get(1)
                        ?: return@mapNotNull null
                    val name = element.text().normalizeTagName()
                    if (name.isBlank()) null else name to id
                }
                .toMap()
        }
    }

    private fun String.normalizeTagName(): String = replace(TAG_COUNT_SUFFIX, "").trim().lowercase().replace(WHITESPACE_REGEX, " ")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private val TAG_URL_REGEX = Regex("""/(artist|parody)/(\d+)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val TAG_COUNT_SUFFIX = Regex("""\s*\(\d+\)\s*$""")
    }

    private val tagLookupCache = mutableMapOf<String, Map<String, String>>()
}
