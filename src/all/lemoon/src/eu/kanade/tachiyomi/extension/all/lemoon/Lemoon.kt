package eu.kanade.tachiyomi.extension.all.lemoon

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Lemoon : KeiSource() {

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("app-name-code", "lemoon-web")
            .set("app-version", "2.0.0")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getComics(comicListUrl(), page)

    // The site's "New Releases" collection, on a single page
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/collections/$NEW_RELEASES_COLLECTION", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("section[aria-labelledby=collection-heading] a[href*='/series/']").map { card ->
            SManga.create().apply {
                url = card.absUrl("href").toHttpUrl().pathSegments.last().substringBefore('-')
                title = card.selectFirst("h2")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.let { ORIGINAL_COVER_REGEX.find(it)?.value }
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = comicListUrl()
        if (query.isNotBlank()) url.addQueryParameter("keywords", query.trim())
        filters.firstInstanceOrNull<GenreFilter>()?.selected?.forEach { url.addQueryParameter("categories[]", it) }
        return getComics(url, page)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val username = url.pathSegments.firstOrNull { it.startsWith("@") }?.removePrefix("@")
        if (username == null || !isSiteUrl(url)) return super.getMangasByUrl(url, page)
        val creatorUrl = "$API_URL/user".toHttpUrl().newBuilder().addPathSegment(username).addPathSegment("comic")
        val data = getComicPage(creatorUrl, page)
        // A creator's list has no format parameter, and novels are text
        return MangasPage(data.items.filter { it.format == "webtoon" }.map { it.toSManga() }, data.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url)) return null
        val index = url.pathSegments.indexOf("series")
        if (index == -1) return null
        val id = url.pathSegments.getOrNull(index + 1)?.substringBefore('-')?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { getDetails(manga.url) } else null
        val episodes = if (fetchChapters) async { getEpisodes(manga.url) } else null
        SMangaUpdate(details?.await() ?: manga, episodes?.await() ?: chapters)
    }

    private suspend fun getDetails(id: String): SManga = client.get("$API_URL/comic/$id", apiHeaders, Fetch.XHR).parseAs<ComicDetailsDto>().toSManga()

    private suspend fun getEpisodes(comicId: String): List<SChapter> {
        val url = "$API_URL/episode".toHttpUrl().newBuilder()
            .addQueryParameter("comicTranslation", comicId)
            .addQueryParameter("order", "position-desc")
            .addQueryParameter("pageSize", "100")
        return getAllPages<EpisodeDto>(url, apiHeaders).map { it.toSChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val locked = chapter.memo.getBooleanOrNull("locked") == true
        val mature = chapter.memo.getBooleanOrNull("mature") == true
        // The site shows these to logged-in members only, so the extension does too
        val token = if (locked || mature) client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == "auth_token" }?.value else null
        if (token == null) {
            if (mature) throw Exception(MATURE_MESSAGE)
            if (locked) return emptyList()
        }
        val contentHeaders = token?.let { apiHeaders.newBuilder().set("Authorization", "Bearer $it").build() } ?: apiHeaders

        val episodeId = chapter.url.substringAfterLast('/')
        val url = "$API_URL/episode/$episodeId/content".toHttpUrl().newBuilder()
            .addQueryParameter("order", "position-asc")
            .addQueryParameter("pageSize", "60")
        return getAllPages<ContentDto>(url, contentHeaders).mapIndexed { i, content -> Page(i, imageUrl = content.media.url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun comicListUrl() = "$API_URL/comic".toHttpUrl().newBuilder().addQueryParameter("format", "webtoon")

    private suspend fun getComics(url: HttpUrl.Builder, page: Int): MangasPage {
        val data = getComicPage(url, page)
        return MangasPage(data.items.map { it.toSManga() }, data.hasNextPage)
    }

    private suspend fun getComicPage(url: HttpUrl.Builder, page: Int): PageDto<ComicDto> {
        url.addQueryParameter("pageNumber", page.toString()).addQueryParameter("pageSize", PAGE_SIZE.toString())
        return client.get(url.build(), apiHeaders, Fetch.XHR).parseAs()
    }

    private suspend inline fun <reified T> getAllPages(url: HttpUrl.Builder, headers: Headers): List<T> {
        val items = mutableListOf<T>()
        var page = 0
        do {
            page++
            val data = client.get(url.setQueryParameter("pageNumber", page.toString()).build(), headers, Fetch.XHR).parseAs<PageDto<T>>()
            items += data.items
        } while (data.hasNextPage)
        return items
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    companion object {
        private const val API_URL = "https://api-srv.lemoon.io/api"
        private const val PAGE_SIZE = 30
        private const val NEW_RELEASES_COLLECTION = "1685422489-new-releases"
        private const val MATURE_MESSAGE = "Mature episode: log in to LEMOON in WebView to read it"

        // The page shows resized covers (<cdn>/comic-cover/<name>/w_640,q_60/<name>.webp)
        private val ORIGINAL_COVER_REGEX = Regex("""^https://[^/]+/comic-cover/[^/]+""")
    }
}
