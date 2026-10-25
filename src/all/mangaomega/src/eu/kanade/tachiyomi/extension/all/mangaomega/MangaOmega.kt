package eu.kanade.tachiyomi.extension.all.mangaomega

import com.squareup.zstd.okio.zstdCompress
import com.squareup.zstd.okio.zstdDecompress
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import org.jsoup.Jsoup
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class MangaOmega : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 4, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        series().sortedByDescending { it.chapters.size }.map { it.toSManga() },
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(
        series().sortedByDescending { it.chapters.maxOf(CachedPost::date) }.map { it.toSManga() },
        false,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val key = query.toKey()
        return MangasPage(
            series().filter { key in it.key }.sortedByDescending { it.chapters.size }.map { it.toSManga() },
            false,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        val series = series()
        val key = series.flatMap { it.posts }.firstOrNull { it.slug == slug }?.key ?: return null
        return series.first { it.key == key }.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addQueryParameter("s", manga.title)
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = series().firstOrNull { it.key == manga.url }
            ?: return SMangaUpdate(manga, chapters)
        val (chapterLabel, partLabel) = when (lang) {
            "es" -> "Capítulo" to "Parte"
            else -> "Chapter" to "Part"
        }
        return SMangaUpdate(
            series.toSManga(),
            series.chapters.map { it.toSChapter(chapterLabel, partLabel) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.memo["slug"]!!.string}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.toInt()
        val url = "$baseUrl/wp-json/wp/v2/posts/$id".toHttpUrl().newBuilder()
            .addQueryParameter("_fields", "content")
            .build()
        val post = try {
            client.get(url).parseAs<ContentDto>()
        } catch (e: HttpException) {
            if (e.code != 404) throw e
            // deleted posts never show up in modified_after, so drop them once noticed
            removePost(id)
            return emptyList()
        }
        return Jsoup.parseBodyFragment(post.content.rendered, baseUrl)
            .select("img:not(.alignleft)")
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Referer")
        .removeHeader("Origin")
        .build()

    private class Series(val key: String, val posts: List<CachedPost>, val chapters: List<CachedPost>) {
        fun toSManga() = SManga.create().apply {
            url = key
            title = posts.first().title
            thumbnail_url = posts.firstNotNullOfOrNull { it.cover }
        }
    }

    private suspend fun series(): List<Series> = posts()
        .filter { it.lang == lang }
        .groupBy { it.key }
        .mapNotNull { (key, posts) ->
            val chapters = posts.filter { it.hasPages }
            if (chapters.isEmpty()) return@mapNotNull null
            Series(
                key = key,
                posts = posts.sortedByDescending { it.date },
                chapters = chapters.sortedWith(
                    compareByDescending<CachedPost> { it.chapterNumber }.thenByDescending { it.date },
                ),
            )
        }

    private suspend fun posts(): List<CachedPost> = cacheMutex.withLock {
        val now = Clock.System.now()
        val cached = readCache()
        val posts = if (cached == null) {
            fetchPosts(modifiedAfter = null).mapNotNull { it.toCachedPost() }
        } else {
            val syncedAt = Instant.fromEpochMilliseconds(cached.syncedAt)
            if (now - syncedAt <= SYNC_INTERVAL) return@withLock cached.posts
            // posts start without pages and get edited later, so sync on modification time
            val changed = fetchPosts(modifiedAfter = syncedAt - SYNC_OVERLAP)
            val changedIds = changed.mapTo(HashSet()) { it.id }
            cached.posts.filterNot { it.id in changedIds } + changed.mapNotNull { it.toCachedPost() }
        }
        val cache = PostCache(now.toEpochMilliseconds(), posts)
        writeCache(cache)
        cache.posts
    }

    private suspend fun removePost(id: Int) = cacheMutex.withLock {
        val cached = readCache() ?: return@withLock
        writeCache(PostCache(cached.syncedAt, cached.posts.filterNot { it.id == id }))
    }

    private suspend fun fetchPosts(modifiedAfter: Instant?): List<PostDto> = coroutineScope {
        fun url(page: Int) = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("per_page", "100")
            addQueryParameter("page", page.toString())
            addQueryParameter("orderby", "id")
            addQueryParameter("order", "asc")
            addQueryParameter("_fields", "id,slug,title,date_gmt,categories,content")
            modifiedAfter?.let { addQueryParameter("modified_after", it.toString()) }
        }.build()

        val firstPage = client.get(url(1), CacheControl.FORCE_NETWORK)
        val totalPages = firstPage.header("X-WP-TotalPages")?.toInt() ?: 1
        val rest = (2..totalPages).map { page ->
            async { client.get(url(page), CacheControl.FORCE_NETWORK).parseAs<List<PostDto>>() }
        }
        (firstPage.parseAs<List<PostDto>>() + rest.awaitAll().flatten()).distinctBy { it.id }
    }

    private fun readCache(): PostCache? {
        if (!cacheFile.exists()) return null
        // unreadable after a schema change between versions, a full sync rebuilds it
        return runCatching { cacheFile.source().zstdDecompress().buffer().parseAs<PostCache>() }.getOrNull()
    }

    private fun writeCache(cache: PostCache) {
        val dir = cacheFile.parentFile!!.apply { mkdirs() }
        val tmpFile = File.createTempFile("posts", ".tmp", dir)
        tmpFile.sink().zstdCompress().buffer().use { it.writeUtf8(cache.toJsonString()) }
        if (!tmpFile.renameTo(cacheFile)) tmpFile.delete()
    }

    companion object {
        private val cacheMutex = Mutex()
        private val cacheFile: File get() = applicationContext.cacheDir.resolve("mangaomega/posts.json.zst")

        private val SYNC_INTERVAL = 10.minutes
        private val SYNC_OVERLAP = 1.days
    }
}
