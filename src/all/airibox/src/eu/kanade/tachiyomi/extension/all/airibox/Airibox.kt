package eu.kanade.tachiyomi.extension.all.airibox

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class Airibox : KeiSource() {

    // LiteSpeed answers pages asked for less than about two seconds apart with a bot check (403); images are not limited
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 1, period = 2.seconds) {
        it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/wp-content/")
    }

    // The site has a single list, newest first
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getPostList(listUrl(page).build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getPostList(listUrl(page).addQueryParameter("s", query.trim()).build())

    private fun listUrl(page: Int) = baseUrl.toHttpUrl().newBuilder().apply { if (page > 1) addPathSegments("page/$page/") }

    // Each post of a list comes with all its photos: the first one is its cover
    private suspend fun getPostList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Search and post pages also show the four newest posts, titled with an h3
        val mangas = document.select("li.wp-block-post:has(h2.wp-block-post-title)").map { post ->
            SManga.create().apply {
                val link = post.selectFirst("h2 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = post.selectFirst(IMAGE_SELECTOR)?.absUrl("src")
                // A post's own page doesn't show its date
                memo = buildJsonObject { put("date", post.selectFirst("time")?.attr("datetime")) }
            }
        }
        return MangasPage(mangas, document.selectFirst("a.wp-block-query-pagination-next") != null)
    }

    // The list's pages (/page/<n>/) and posts (/<slug>/)
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || !(segments.isEmpty() || (segments.size == 2 && segments[0] == "page"))) {
            return super.getMangasByUrl(url, page)
        }
        val firstPage = segments.getOrNull(1)?.toIntOrNull() ?: 1
        return getPostList(listUrl(firstPage + page - 1).apply { url.queryParameter("s")?.let { addQueryParameter("s", it) } }.build())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 1) return null
        // The site writes its percent escapes in lower case
        val manga = SManga.create().apply { this.url = "/${segments[0].replace(ESCAPE_REGEX) { it.value.lowercase() }}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val images = document.select(IMAGE_SELECTOR)
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.wp-block-post-title")!!.text()
            description = MarkdownDescription.Builder().info("${images.size} photos").build()
            thumbnail_url = images.firstOrNull()?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = Instant.tryParse(manga.memo["date"]?.stringOrNull)
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(IMAGE_SELECTOR)
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    companion object {
        private const val IMAGE_SELECTOR = ".wp-block-post-content img"
        private val ESCAPE_REGEX = Regex("%[0-9A-F]{2}")
    }
}
