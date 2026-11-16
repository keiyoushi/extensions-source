package eu.kanade.tachiyomi.extension.en.eightmusesxxx

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Source
abstract class EightMusesXxx : KeiSource() {

    override val supportsLatest get() = false

    override val supportsRelatedMangas get() = true

    private val rscHeaders get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int) = getComicList(listUrl("comics", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return getComicList(searchUrl(query.trim(), page))
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.slug
        return getComicList(listUrl(category?.let { "category/$it" } ?: "comics", page))
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSiteUrl(url)) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        return when (segments.first()) {
            "category", "tag" -> getComicList(listUrl("${segments[0]}/${segments.getOrElse(1) { "" }}", page))
            "comics" -> if (comicSlug(url) != null) super.getMangasByUrl(url, page) else getPopularManga(page)
            else -> url.queryParameter("s")?.let { getComicList(searchUrl(it, page)) } ?: MangasPage(emptyList(), false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url)) return null
        val manga = SManga.create().apply { this.url = comicSlug(url) ?: return null }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun comicSlug(url: HttpUrl) = url.pathSegments.takeIf { it.first() == "comics" }
        ?.getOrNull(1)
        ?.takeIf { it.isNotEmpty() && it != "page" }

    private fun listUrl(path: String, page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegments(path)
        if (page > 1) addPathSegments("page/$page")
    }.build()

    private fun searchUrl(query: String, page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        if (page > 1) addPathSegments("page/$page")
        addQueryParameter("s", query)
    }.build()

    private suspend fun getComicList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.extractNextJs<ComicListDto>()?.items.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("nav.pagination a.pagination-direction[aria-disabled=false]:last-child") != null)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = getComicPage(getMangaUrl(manga)).comic
        return SMangaUpdate(comic.toSManga(), comic.toSChapters())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = getComicPage(getMangaUrl(manga)).recommendations.map { it.toSManga() }

    // Without an empty _rsc parameter, the site redirects every RSC request to the same URL with one
    private suspend fun getComicPage(url: String) = client.get(url.toHttpUrl().newBuilder().addQueryParameter("_rsc", null).build(), rscHeaders, Fetch.XHR)
        .extractNextJs<ComicPageDto>()!!

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comics/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = getComicPage(getChapterUrl(chapter)).comic.pages
        .filter { it.label == "page" }
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }

    override val supportsFilterFetching get() = true

    // The site has no list of its categories besides its sitemap
    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/sitemap-taxonomies.xml", Fetch.DOCUMENT)
        .asJsoup(Parser.xmlParser())
        .select("loc")
        .map { it.text().toHttpUrl().pathSegments }
        .filter { it.size == 2 && it[0] == "category" }
        .map { it[1] }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<String>>())
}
