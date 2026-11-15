package eu.kanade.tachiyomi.extension.es.sinergiasincontrol

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Source
abstract class SinergiaSinControl : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(comic()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(listOf(comic()).filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = comic().takeIf { url.host == baseUrl.toHttpUrl().host }
        ?.apply { initialized = true }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) comic() else manga
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        // The feed gives at most 150 posts at a time
        val first = feed(1)
        val rest = coroutineScope {
            (1 + PAGE_SIZE..first.totalResults step PAGE_SIZE).map { async { feed(it) } }.awaitAll()
        }
        val posts = (first.entry + rest.flatMap { it.entry }).filter { it.hasImage }
        // Posts are numbered in order: the strip numbers in titles skip some, have typos and leave out the extras
        return SMangaUpdate(details, posts.mapIndexed { i, post -> post.toSChapter(posts.size - i) })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The blog's template puts its license badge under every post
        return document.select(".post-body img")
            .filterNot { it.parent()?.attr("rel") == "license" }
            .mapNotNull { it.absUrl("src").toHttpUrlOrNull() }
            .mapIndexed { i, url -> Page(i, imageUrl = url.originalSize()) }
    }

    private suspend fun feed(start: Int): FeedDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("feeds/posts/default")
            .addQueryParameter("alt", "json")
            .addQueryParameter("start-index", start.toString())
            .addQueryParameter("max-results", PAGE_SIZE.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs<FeedResponseDto>().feed
    }

    private fun comic() = SManga.create().apply {
        url = "/"
        title = name
        author = "Ender Wiggins"
        artist = author
        description = MarkdownDescription.Builder()
            .description("Tira cómica sobre informática, frikis y sus frikismos, geeks, política y actualidad.")
            .build()
        thumbnail_url = COVER_URL
    }

    // Blogger serves its images at the size written in their URL, s0 being the original one
    private fun HttpUrl.originalSize(): String {
        val url = toString()
        if (!host.endsWith(".googleusercontent.com") && !host.endsWith(".bp.blogspot.com")) return url
        return url.replace(SIZE_SEGMENT_REGEX, "/s0/").replace(SIZE_SUFFIX_REGEX, "=s0")
    }

    private companion object {
        const val PAGE_SIZE = 150
        const val COVER_URL = "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhrA0-mnVtOJILu8brf7-yM0Cqc8bVCYBhPsLzNBGtgaNFQETVLlRmd6BJGPpIvZMT1TzGHaO905VEV0b-eUlB5cB_WghgTRVTrkLmLOFWS13wm_Wgl5fbTelV1CkXPW_8SpOneKnAjVMSs/s0/fred.gif"

        // Like /s400/ or /w400-h283/ before the file name, or =w400-h360 at the end
        val SIZE_SEGMENT_REGEX = Regex("""/[sw]\d+[^/]*/(?=[^/]+$)""")
        val SIZE_SUFFIX_REGEX = Regex("""=[sw]\d+[^/]*$""")
    }
}
