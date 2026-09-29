package eu.kanade.tachiyomi.extension.en.myhentaigallery

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MyHentaiGallery : KeiSource() {

    // =============================== Popular ================================

    override suspend fun getPopularManga(page: Int): MangasPage = parseComicListing("$baseUrl/views/$page".toHttpUrl())

    // =============================== Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseComicListing("$baseUrl/gpage/$page".toHttpUrl())

    // =============================== Search =================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val id = when (url.pathSegments[0]) {
            "gallery" -> url.pathSegments.getOrNull(2)
            "g", "a" -> url.pathSegments.getOrNull(1)
            else -> null
        }?.takeIf { it.all(Char::isDigit) } ?: return null

        val document = client.get("$baseUrl/g/$id").asJsoup()
        return mangaDetailsParse(SManga.create().apply { this.url = "/g/$id" }, document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        genreSearchUrl(query, page)?.let { return parseComicListing(it) }

        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(page.toString())
                .addQueryParameter("query", query)
                .build()
            return parseComicListing(url)
        }

        val categoryFilter = filters.firstInstanceOrNull<GenreFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val tagLookupFilter = filters
            .filterIsInstance<TagLookupFilter>()
            .firstOrNull { it.state.isNotBlank() }

        if (tagLookupFilter != null) {
            val tagId = tagLookupFilter.resolveTagId()
            return parseComicListing("$baseUrl/a/${tagLookupFilter.uriPart}/$tagId/$page".toHttpUrl())
        }

        if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
            val catId = categoryFilter.toUriPart()
            return parseComicListing("$baseUrl/g/category/$catId/$page".toHttpUrl())
        }

        val sortPath = sortFilter?.toUriPart() ?: "gpage"
        return parseComicListing("$baseUrl/$sortPath/$page".toHttpUrl())
    }

    // ============================== Filters =================================

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    private suspend fun parseComicListing(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedChapters = listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = document.location().substringAfter(baseUrl)
            },
        )

        return SMangaUpdate(mangaDetailsParse(manga, document), updatedChapters)
    }

    private fun mangaDetailsParse(manga: SManga, document: Document): SManga {
        val info: Element = document.selectFirst("div.comic-header")!!
        val categories = info.select("div:containsOwn(categories) a").eachText()
        val artists = info.select("div:containsOwn(artists) a").eachText()
        val parodies = info.select("div:containsOwn(parodies) a").eachText()

        return manga.apply {
            title = info.selectFirst("h1")!!.text()
            genre = (
                categories +
                    artists.map { "$ARTIST_GENRE_PREFIX$it" } +
                    parodies.map { "$PARODY_GENRE_PREFIX$it" }
                ).joinToString()
            artist = artists.joinToString()
            thumbnail_url = document.selectFirst(".comic-listing .comic-inner img")?.absUrl("src")?.encodeSpaces()
            status = SManga.COMPLETED
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

    // ============================== Page List ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select("div.comic-thumb img[src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src").replace("/thumbnail/", "/original/").encodeSpaces())
    }

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    // Routes a clicked artist/parody genre chip to its tag listing instead of a title search.
    private suspend fun genreSearchUrl(query: String, page: Int): HttpUrl? {
        val (uriPart, name) = when {
            query.startsWith(ARTIST_GENRE_PREFIX) -> "artist" to query.removePrefix(ARTIST_GENRE_PREFIX)
            query.startsWith(PARODY_GENRE_PREFIX) -> "parody" to query.removePrefix(PARODY_GENRE_PREFIX)
            else -> return null
        }
        val id = lookupTagId(uriPart, name)
            ?: throw Exception("No $uriPart \"$name\" was found.")
        return "$baseUrl/a/$uriPart/$id/$page".toHttpUrl()
    }

    private suspend fun TagLookupFilter.resolveTagId(): String {
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

    private suspend fun lookupTagId(uriPart: String, name: String): String? {
        val lookup = tagLookupCache.getOrPut(uriPart) { loadTagLookup(uriPart) }
        return lookup[name.normalizeTagName()]
    }

    private suspend fun loadTagLookup(uriPart: String): Map<String, String> {
        val tagUrlRegex = Regex("""/$uriPart/(\d+)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

        return client.get("$baseUrl/tag/$uriPart").asJsoup()
            .select("a[href*='/$uriPart/']")
            .mapNotNull { element ->
                val id = tagUrlRegex.find(element.attr("href"))?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val name = element.text().normalizeTagName()
                if (name.isBlank()) null else name to id
            }
            .toMap()
    }

    private fun String.normalizeTagName(): String = replace(TAG_COUNT_SUFFIX, "").trim().lowercase().replace(WHITESPACE_REGEX, " ")

    companion object {
        private const val ARTIST_GENRE_PREFIX = "Artist: "
        private const val PARODY_GENRE_PREFIX = "Parody: "
        private val TAG_URL_REGEX = Regex("""/(artist|parody)/(\d+)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val TAG_COUNT_SUFFIX = Regex("""\s*\(\d+\)\s*$""")
    }

    private val tagLookupCache = mutableMapOf<String, Map<String, String>>()
}
