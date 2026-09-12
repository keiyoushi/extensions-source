package eu.kanade.tachiyomi.extension.en.hentaipill

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class HentaiPill : KeiSource() {

    override val supportsLatest = true

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/popular")
        return parseMangasPage(response.asJsoup())
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/category/doujin" else "$baseUrl/category/doujin/$page"
        val response = client.get(url)
        return parseMangasPage(response.asJsoup())
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = getSearchUrl(page, query, filters)
        val response = client.get(url)
        return parseMangasPage(response.asJsoup())
    }

    private fun getSearchUrl(page: Int, query: String, filters: FilterList): String {
        val cleanQuery = query.trim()

        // 1. Text Search (Sort applies here)
        if (cleanQuery.isNotEmpty()) {
            return baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addQueryParameter("page", page.toString())
                addQueryParameter("q", cleanQuery)

                val sortFilter = filters.firstInstanceOrNull<SortFilter>()
                when (sortFilter?.state) {
                    1 -> addQueryParameter("sort", "popular")
                    2 -> addQueryParameter("sort", "relevant")
                }
            }.build().toString()
        }

        // Extract text filters and format them as slugs
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.state?.normalize() ?: ""
        val parody = filters.firstInstanceOrNull<ParodyFilter>()?.state?.normalize() ?: ""
        val character = filters.firstInstanceOrNull<CharacterFilter>()?.state?.normalize() ?: ""
        val artist = filters.firstInstanceOrNull<ArtistFilter>()?.state?.normalize() ?: ""
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: ""

        // Determine path based on hierarchy
        val path = when {
            genre.isNotEmpty() -> "genre/$genre"
            parody.isNotEmpty() -> "parody/$parody"
            character.isNotEmpty() -> "character/$character"
            artist.isNotEmpty() -> "artist/$artist"
            category.isNotEmpty() -> category
            else -> {
                // 2. Default Search (Sort also applies here if nothing else is selected)
                return baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("search")
                    addQueryParameter("page", page.toString())
                    val sortFilter = filters.firstInstanceOrNull<SortFilter>()
                    when (sortFilter?.state) {
                        1 -> addQueryParameter("sort", "popular")
                        2 -> addQueryParameter("sort", "relevant")
                    }
                }.build().toString()
            }
        }

        // 3. Path-based routing for tags and categories (Sort is ignored here by the site)
        return if (path == "popular" || path == "rising") {
            "$baseUrl/$path" // These lists are static and do not use pagination
        } else {
            if (page == 1) "$baseUrl/$path" else "$baseUrl/$path/$page"
        }
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("div.galleries-list > div.gallery-item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a[href*=/gallery/]")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst("span")!!.text()
                thumbnail_url = link.selectFirst("img.lazy")?.attr("abs:data-src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "gallery") return null

        val response = client.get(url)
        return parseMangaDetails(response.asJsoup()).apply {
            this.url = url.encodedPath
        }
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(baseUrl + manga.url)
        val document = response.asJsoup()

        return SMangaUpdate(
            manga = if (fetchDetails) parseMangaDetails(document) else manga,
            chapters = if (fetchChapters) parseChapterList(document, manga.url) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.reading-page-header h1")!!.text()
        thumbnail_url = document.selectFirst("button.js-button-favorite")?.attr("abs:data-cover")
        artist = document.select(".table-row:has(.table-label:contains(Artists)) .table-value a").joinToString { it.text() }
        author = artist
        genre = document.select(".table-row:has(.table-label:contains(Tags)) .table-value a").joinToString { it.text() }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        val parodies = document.select(".table-row:has(.table-label:contains(Parodies)) .table-value a").joinToString { it.text() }
        val characters = document.select(".table-row:has(.table-label:contains(Characters)) .table-value a").joinToString { it.text() }

        description = buildString {
            if (parodies.isNotEmpty()) appendLine("Parodies: $parodies")
            if (characters.isNotEmpty()) appendLine("Characters: $characters")
        }.trim()
        initialized = true
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ============================= Chapters ==============================

    private fun parseChapterList(document: Document, mangaUrl: String): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            url = mangaUrl
            date_upload = dateFormat.tryParse(document.selectFirst("div.reading-page-header-data span")?.ownText())
        },
    )

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        val document = response.asJsoup()
        return document.select("div.reading-pages-content img.lazy").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:data-src"))
        }
    }

    // ============================== Related ==============================

    override val supportsRelatedMangas = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Text search takes priority over all filters."),
        Filter.Header("Sort only applies to Search and 'Any' category."),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Only one text filter can be used at a time."),
        GenreFilter(),
        ParodyFilter(),
        CharacterFilter(),
        ArtistFilter(),
        Filter.Separator(),
        CategoryFilter(),
    )

    // ============================= Utilities =============================

    private fun String.normalize(): String = this.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .removePrefix("-")
        .removeSuffix("-")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
