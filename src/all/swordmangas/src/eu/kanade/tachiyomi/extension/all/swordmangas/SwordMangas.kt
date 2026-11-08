package eu.kanade.tachiyomi.extension.all.swordmangas

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class SwordMangas : KeiSource() {

    // The site serves every series and chapter in both languages, under /esp and /en
    private val english get() = lang == "en"

    private val langPath get() = if (english) "en" else "esp"

    override suspend fun getPopularManga(page: Int) = getLibraryPage(libraryUrl("popular", null), page)

    // The home page's latest updates
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val latest = client.get("$baseUrl/$langPath?page=$page", Fetch.DOCUMENT).asJsoup().extractNextJs<LatestDto>()!!
        return MangasPage(latest.items.map { it.toSManga(english) }, latest.currentPage < latest.totalPages)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = libraryUrl(filters.firstInstance<SortFilter>().value, filters.firstInstance<GenreFilter>().value)
        if (query.isBlank()) return getLibraryPage(url, page)

        // The site's search box queries its database from the browser: look through the whole catalogue instead,
        // a few dozen series
        val mangas = mutableListOf<SManga>()
        var libraryPage = 1
        do {
            val result = getLibraryPage(url, libraryPage++)
            mangas += result.mangas
        } while (result.hasNextPage)
        return MangasPage(mangas.filter { it.title.contains(query.trim(), ignoreCase = true) }, false)
    }

    private fun libraryUrl(sort: String, genre: String?) = "$baseUrl/$langPath/library".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .apply { if (genre != null) addQueryParameter("genre", genre) }
        .build()

    private suspend fun getLibraryPage(library: HttpUrl, page: Int): MangasPage {
        val pageUrl = library.newBuilder().addQueryParameter("page", page.toString()).build()
        val document = try {
            client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        } catch (e: HttpException) {
            // The English catalogue answers 451 (Unavailable For Legal Reasons) in some countries, while the Spanish
            // one, with the same series under their Spanish titles, doesn't
            if (e.code != 451 || !english) throw e
            client.get(pageUrl.newBuilder().setPathSegment(0, "esp").build(), Fetch.DOCUMENT).asJsoup()
        }
        val mangas = document.select("a[href*='/series/']:has(img[alt])").map { card ->
            val img = card.selectFirst("img")!!
            SManga.create().apply {
                url = card.absUrl("href").toHttpUrl().pathSegments.last()
                title = img.attr("alt")
                thumbnail_url = img.absUrl("src").toHttpUrl().queryParameter("url")
            }
        }
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val first = segments.first()
        if (first in LANG_PATHS && first != langPath) return null
        val section = segments.indexOfFirst { it == "series" || it == "read" }
        if (section < 0) return null
        val slug = segments.getOrNull(section + 1)?.takeIf { it.isNotEmpty() } ?: return null
        return getSeriesPage(slug).serie.toSManga(english)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$langPath/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$langPath/read/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = getSeriesPage(manga.url)
        return SMangaUpdate(series.serie.toSManga(english), series.sortedChapters.map { it.toSChapter(series.serie.slug) })
    }

    private suspend fun getSeriesPage(slug: String) = client.get("$baseUrl/$langPath/series/$slug", Fetch.DOCUMENT)
        .asJsoup()
        .extractNextJs<SeriesPageDto>()!!

    // A locked chapter's page has no image list
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .asJsoup()
        .extractNextJs<ReaderDto>()
        ?.initialChapterData
        ?.get(if (english) "ENG" else "ESP")
        .orEmpty()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter(), GenreFilter())

    companion object {
        private val LANG_PATHS = setOf("esp", "en")
    }
}
