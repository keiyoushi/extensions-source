package eu.kanade.tachiyomi.extension.en.yiffcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class YiffComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getComicList(baseUrl.toHttpUrl(), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val orientation = filters.firstInstanceOrNull<OrientationFilter>()?.selected
        val tag = filters.filterIsInstance<TagFilter>().firstNotNullOfOrNull { it.selected }
        val url = when {
            query.isNotBlank() -> siteUrl("search/").newBuilder().addQueryParameter("q", query.trim()).build()
            orientation != null -> siteUrl("orientation/$orientation/")
            tag != null -> siteUrl("genre/$tag/")
            else -> baseUrl.toHttpUrl()
        }
        return getComicList(url, page)
    }

    private suspend fun getComicList(url: HttpUrl, page: Int): MangasPage {
        val pageUrl = if (page > 1) url.newBuilder().setQueryParameter("page", page.toString()).build() else url
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("main a[href^=/yiff/]:has(img)").map { it.toSManga() }
        // The last page has no next link, and the one after it is a 404
        return MangasPage(mangas, document.selectFirst("a[aria-label='Next page']") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst("p")!!.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    // The comics of an artist, tag, character or orientation, and searches
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter(String::isNotEmpty)
        val isList = url.isSite() &&
            when (segments.firstOrNull()) {
                "artist", "genre", "orientation" -> segments.size == 2
                "search" -> segments.size == 1 && !url.queryParameter("q").isNullOrBlank()
                else -> false
            }
        if (!isList) return super.getMangasByUrl(url, page)
        val firstPage = url.queryParameter("page")?.toIntOrNull() ?: 1
        val listUrl = url.newBuilder().host(baseUrl.toHttpUrl().host).removeAllQueryParameters("page").build()
        return getComicList(listUrl, firstPage + page - 1)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1)
        if (!url.isSite() || url.pathSegments[0] != "yiff" || slug.isNullOrEmpty()) return null
        val manga = SManga.create().apply { this.url = "/yiff/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val article = document.selectFirst("article")!!
        val reader = document.extractNextJs<ReaderDto>()!!
        val artists = article.select("a[href^=/artist/]").map { it.text() }.distinct().joinToString().ifEmpty { null }
        val details = SManga.create().apply {
            url = manga.url
            title = article.selectFirst("h1")!!.text()
            // Full size (cover.webp or cover-hd.webp), where lists show 640 px wide covers
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            author = artists
            artist = artists
            genre = article.select("a[href^=/orientation/], a[href^=/genre/]").map { it.text() }.distinct().joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info("${reader.pageCount} pages")
                .build()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = Instant.tryParse(article.selectFirst("header time")?.attr("datetime"))
            // The comic page holds the reader: its pages are kept so that reading needs no request
            memo = reader.toJsonElement().obj
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = chapter.memo.parseAs<ReaderDto>().toPages()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("article a[href^=/yiff/]:has(img)")
        .map { it.toSManga() }

    override val supportsFilterFetching = true

    // The 200 most used tags and characters
    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(siteUrl("genre/"), Fetch.DOCUMENT).asJsoup()
        fun tags(category: String) = document.select("a.gt-$category").map {
            TagDto(it.selectFirst("span")!!.text(), it.absUrl("href").toHttpUrl().pathSegments[1])
        }
        return TagListsDto(tags("GENERAL"), tags("CHARACTER")).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<TagListsDto>())

    private fun siteUrl(path: String) = baseUrl.toHttpUrl().resolve(path)!!

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host
}
