package eu.kanade.tachiyomi.extension.all.nhentaiyaoi

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NhentaiYaoi : MadaraNoAjax() {

    // Search results have no post id: with paths, searches follow the site's pages
    override val supportsPostId = false

    override val chapterMode = ChapterMode.MangaAjax

    // The site's author field holds the language: the artists are the authors and the language goes with the genres
    override val mangaDetailsSelectorAuthor = "div.artist-content > a"

    override val seriesTypeSelector = ".post-content_item:contains(Language) .summary-content"

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("pt-BR"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value.orEmpty()
        val language = filters.firstInstanceOrNull<LanguageFilter>()?.value
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        val url = if (query.isBlank()) {
            archiveUrlBuilder(page, sort, genre?.path ?: "/$mangaSubString/", "").apply {
                language?.let { addEncodedQueryParameter("wp-manga-author", it.slug) }
            }
        } else {
            // Archive pages with a query redirect to the home page, and the search page drops the genre for a language
            searchUrlBuilder(page, query).apply {
                if (sort.isNotEmpty()) addQueryParameter("m_orderby", sort)
                language?.let { addQueryParameter("author", it.name) } ?: genre?.let { addQueryParameter("genre[]", it.slug) }
            }
        }
        return listPage(url.build())
    }

    override suspend fun archivePage(page: Int, order: String, path: String, query: String) = listPage(archiveUrlBuilder(page, order, path, query).build())

    private suspend fun listPage(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Searches without results, missing lists and pages past the end redirect to the home page
        if (document.location() == "$baseUrl/") return MangasPage(emptyList(), false)
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        if (segments.size >= 2 && segments[0] in LIST_DIRECTORIES) {
            return archivePage(page, "", "/${segments[0]}/${segments[1]}/")
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] != mangaSubString) return null
        return super.getMangaByUrl(baseUrl.toHttpUrl().newBuilder().encodedPath("/${segments[0]}/${segments[1]}/").build())
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga {
        // Missing series redirect to the home page
        if (document.location() == "$baseUrl/") throw HttpException(404)
        return super.parseDetails(document, id, preserveUrl).apply {
            if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Pages are linked over http, which redirects to https
    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.replaceFirst("http://", "https://")

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$mangaSubString/", Fetch.DOCUMENT).asJsoup()
        val genres = document.select("$filterGenresSelector a[href*='/$genreDirectory/']").map { it.toRoute(it.ownText()) }
        val languages = document.select(".second-menu a[href*='/manga-author/']").map { it.toRoute(it.text()) }
        return FilterData(genres.distinctBy(GenreRoute::slug), languages).toJsonElement()
    }

    private fun Element.toRoute(name: String): GenreRoute {
        val path = attr("abs:href").toHttpUrl().encodedPath
        return GenreRoute(name, path.trimEnd('/').substringAfterLast('/'), path)
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return FilterList(
            buildList {
                add(SortFilter(intl["order_by_filter_title"], orderByFilterOptions))
                filterData?.languages?.takeIf(List<GenreRoute>::isNotEmpty)?.let { add(LanguageFilter(it)) }
                filterData?.genres?.takeIf(List<GenreRoute>::isNotEmpty)?.let { add(GenreFilter(intl["genre_filter_title"], it)) }
                add(Filter.Header("With a search text, the site ignores the genre when a language is chosen"))
            },
        )
    }

    companion object {
        private val LIST_DIRECTORIES = setOf("manga-genre", "manga-tag", "manga-author", "manga-artist")

        // WordPress resizes ("-175x238") of the original cover
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
