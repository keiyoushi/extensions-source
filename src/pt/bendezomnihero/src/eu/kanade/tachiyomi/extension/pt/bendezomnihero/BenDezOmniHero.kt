package eu.kanade.tachiyomi.extension.pt.bendezomnihero

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BenDezOmniHero : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchEntries().map { it.toPostManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = fetchEntries().map { it.toPostManga() }
        val filtered = if (query.isBlank()) {
            comics
        } else {
            comics.filter { it.title.contains(query, ignoreCase = true) }
        }
        return MangasPage(filtered, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return fetchEntries()
            .firstOrNull { it.postPath() == url.encodedPath }
            ?.toPostManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val entry = fetchEntries().firstOrNull { it.postPath() == manga.url }
            ?: error("Post not found: ${manga.url}")
        val chapter = SChapter.create().apply {
            url = entry.postPath()
            name = "Oneshot"
            chapter_number = 1f
            date_upload = entry.publishedEpoch()
        }
        return SMangaUpdate(entry.toPostManga(), listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".post-body img[src^=http]")
            .mapNotNull { img ->
                img.attr("data-src")
                    .ifBlank { img.attr("abs:src") }
                    .takeIf { it.startsWith("http") && ("blogger.googleusercontent.com" in it || "bp.blogspot.com" in it) }
                    // Original-size Blogger image (s0 size segment)
                    ?.let { bloggerSizeRegex.replaceFirst(it, "/s0/$1") }
            }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private suspend fun fetchEntries(): List<BloggerEntry> {
        val entries = mutableListOf<BloggerEntry>()
        var startIndex = 1
        while (true) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("feeds/posts/default")
                .addQueryParameter("alt", "json")
                .addQueryParameter("max-results", MAX_RESULTS.toString())
                .addQueryParameter("start-index", startIndex.toString())
                .build()
            val feed = client.get(url, Fetch.XHR)
                .parseAs<BloggerFeedResponse>()
                .feed
            if (feed.entry.isEmpty()) break
            entries.addAll(feed.entry)
            if (feed.entry.size < MAX_RESULTS) break
            startIndex += MAX_RESULTS
        }
        return entries.filter { it.isReadableComic() }.distinctBy { it.postPath() }
    }

    private val bloggerSizeRegex = Regex("""/[sw]\d[\w-]*/([^/]+)$""")

    companion object {
        private const val MAX_RESULTS = 500
    }
}
