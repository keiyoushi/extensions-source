package eu.kanade.tachiyomi.extension.ja.hentaione

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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiOne : KeiSource() {

    // Cloudflare answers 429 past 15 pages in 10 seconds, cached ones included; the image host has no limit
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 1, period = 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", getFilters())

    override suspend fun getLatestUpdates(page: Int) = getGalleryList(baseUrl.toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val list = filters.firstInstance<ListFilter>()
        val url = if (query.isNotBlank()) {
            siteUrl("/articles/search").newBuilder().addQueryParameter("keyword", query.trim())
        } else {
            siteUrl(list.path).newBuilder()
        }
        if (filters.firstInstance<SortFilter>().popular && (query.isNotBlank() || !list.isRanking)) {
            url.addQueryParameter("type", "popular")
        }
        return getGalleryList(url.build(), page)
    }

    private fun siteUrl(path: String) = baseUrl.toHttpUrl().resolve(path)!!

    private suspend fun getGalleryList(url: HttpUrl, page: Int): MangasPage {
        val pageUrl = if (page > 1) url.newBuilder().setQueryParameter("page", page.toString()).build() else url
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        // The home page also shows the top 10 of the day, above the list
        val mangas = document.select("div.container:has(> h1) a.group").map { it.toSManga() }
        return MangasPage(mangas, mangas.isNotEmpty() && document.selectFirst("a[aria-label='next page']") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst("div[title]")!!.attr("title")
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    // The lists of a category, tag, parody, character, artist, circle or language, the rankings, the search and the home page
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val isList = segments.isEmpty() ||
            (segments.size == 2 && ((segments[0] in LIST_TYPES && segments[1].all(Char::isDigit)) || (segments[0] == "articles" && segments[1] in ARTICLE_LISTS)))
        if (url.host != baseUrl.toHttpUrl().host || !isList) return super.getMangasByUrl(url, page)
        val firstPage = url.queryParameter("page")?.toIntOrNull() ?: 1
        return getGalleryList(url.newBuilder().removeAllQueryParameters("page").build(), firstPage + page - 1)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val id = when {
            segments.size == 2 && segments[0] == "articles" -> segments[1]
            segments == listOf("viewer") -> url.queryParameter("articleId")
            else -> null
        }
        if (id.isNullOrEmpty() || !id.all(Char::isDigit)) return null
        val manga = SManga.create().apply { this.url = "/articles/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val info = document.selectFirst("#article-details")!!
        fun names(type: String) = info.select("#article-tag-information a[href^='/$type/']").map { it.text() }
        val artists = names("artists").joinToString().ifEmpty { null }
        val details = SManga.create().apply {
            url = manga.url
            title = info.selectFirst("h1")!!.text()
            artist = artists
            author = names("groups").joinToString().ifEmpty { null } ?: artists
            genre = (names("categories") + names("tags")).joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info("${document.select("a[href^='/viewer'] > div[role=button]").size}ページ")
                .field("原作", names("parodies"))
                .field("キャラクター", names("characters"))
                .build()
            thumbnail_url = info.selectFirst("img")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(info.selectFirst("a[href^='/viewer']")!!.absUrl("href"))
            name = "Chapter"
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .extractNextJs<ViewerDto>()!!
        .toPages()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("h2:contains(関連) + div > a[href^='/articles/']").map { it.toSManga() }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val LIST_TYPES = setOf("categories", "tags", "parodies", "characters", "artists", "groups", "languages")
        private val ARTICLE_LISTS = setOf("rank", "search")
    }
}
