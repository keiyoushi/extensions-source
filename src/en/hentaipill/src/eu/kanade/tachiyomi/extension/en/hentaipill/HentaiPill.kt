package eu.kanade.tachiyomi.extension.en.hentaipill

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class HentaiPill : HttpSource() {

    override val supportsLatest = true

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/category/doujin" else "$baseUrl/category/doujin/$page"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrl().host) {
                if (url.pathSegments.firstOrNull() == "gallery") {
                    val manga = SManga.create().apply {
                        setUrlWithoutDomain(url.toString())
                        initialized = true
                    }
                    return fetchMangaDetails(manga).map {
                        it.url = manga.url
                        it.initialized = true
                        MangasPage(listOf(it), false)
                    }
                }

                throw Exception("Unsupported url")
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val cleanQuery = query.trim()

        // 1. Text Search (Sort applies here)
        if (cleanQuery.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("q", cleanQuery)

                val sortFilter = filters.firstInstanceOrNull<SortFilter>()
                when (sortFilter?.state) {
                    1 -> addQueryParameter("sort", "popular")
                    2 -> addQueryParameter("sort", "relevant")
                }
            }.build()
            return GET(url, headers)
        }

        // Extract text filters and format them as slugs (replace spaces with hyphens)
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.state?.trim()?.replace(" ", "-")?.lowercase() ?: ""
        val parody = filters.firstInstanceOrNull<ParodyFilter>()?.state?.trim()?.replace(" ", "-")?.lowercase() ?: ""
        val character = filters.firstInstanceOrNull<CharacterFilter>()?.state?.trim()?.replace(" ", "-")?.lowercase() ?: ""
        val artist = filters.firstInstanceOrNull<ArtistFilter>()?.state?.trim()?.replace(" ", "-")?.lowercase() ?: ""
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
                val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                    addQueryParameter("page", page.toString())
                    val sortFilter = filters.firstInstanceOrNull<SortFilter>()
                    when (sortFilter?.state) {
                        1 -> addQueryParameter("sort", "popular")
                        2 -> addQueryParameter("sort", "relevant")
                    }
                }.build()
                return GET(url, headers)
            }
        }

        // 3. Path-based routing for tags and categories (Sort is ignored here by the site)
        val finalUrl = if (path == "popular" || path == "rising") {
            "$baseUrl/$path" // These lists are static and do not use pagination
        } else {
            if (page == 1) "$baseUrl/$path" else "$baseUrl/$path/$page"
        }

        return GET(finalUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
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
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = dateFormat.tryParse(document.selectFirst("div.reading-page-header-data span")?.ownText())
            },
        )
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reading-pages-content img.lazy").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
