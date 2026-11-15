package eu.kanade.tachiyomi.extension.ja.nukibooks

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class NukiBooks : KeiSource() {

    // The site's ranking, a single page of 100
    override suspend fun getPopularManga(page: Int) = getGalleryList(listUrl("articles/rank"), page)

    // The site has no list of all its works: the newest of エロ漫画, its main category
    override suspend fun getLatestUpdates(page: Int) = getGalleryList(listUrl("categories/4"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            listUrl("articles/search").newBuilder().addQueryParameter("keyword", query.trim()).build()
        } else {
            listUrl(filters.firstInstance<ListFilter>().path)
        }
        return getGalleryList(url, page)
    }

    private fun listUrl(path: String) = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build()

    private suspend fun getGalleryList(url: HttpUrl, page: Int): MangasPage {
        val pageUrl = if (page > 1) url.newBuilder().setQueryParameter("page", page.toString()).build() else url
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".gallery-list > a.gallery-item").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("a.next") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst(".gallery-item-ttl")!!.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    // The lists of a category, tag, artist, circle, parody or character, the rankings and the search
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val isList = segments.size == 2 &&
            ((segments[0] in LIST_TYPES && segments[1].all(Char::isDigit)) || (segments[0] == "articles" && segments[1] in ARTICLE_LISTS))
        if (url.host != baseUrl.toHttpUrl().host || !isList) return super.getMangasByUrl(url, page)
        val firstPage = url.queryParameter("page")?.toIntOrNull() ?: 1
        return getGalleryList(url.newBuilder().removeAllQueryParameters("page").build(), firstPage + page - 1)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] != "articles" || !segments[1].all(Char::isDigit)) return null
        val manga = SManga.create().apply { this.url = "/articles/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Each row of names comes with its label: 原作, キャラ, サークル, 作者, 内容
        val tags = document.select(".detail-tags").associate { row ->
            row.selectFirst(".tag-ttl")!!.text() to row.select("a.tag").map { it.text() }
        }
        val pages = document.select(PAGE_SELECTOR)
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.detail-ttl")!!.text()
            artist = tags["作者"]?.joinToString()?.ifEmpty { null }
            author = tags["サークル"]?.joinToString()?.ifEmpty { null } ?: artist
            genre = tags["内容"]?.joinToString()?.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info("${pages.size}ページ")
                .apply { tags.filterKeys { it !in DETAIL_ROWS }.forEach { (label, names) -> field(label, names) } }
                .build()
            thumbnail_url = manga.thumbnail_url ?: pages.firstOrNull()?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR).mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("h2.article_under-ttl + .gallery a.gallery-item").map { it.toSManga() }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private const val PAGE_SELECTOR = ".article-page-list > img"
        private val DETAIL_ROWS = setOf("作者", "サークル", "内容")
        private val LIST_TYPES = setOf("categories", "tags", "artists", "groups", "parodies", "characters")
        private val ARTICLE_LISTS = setOf("rank", "trend", "search")
    }
}
