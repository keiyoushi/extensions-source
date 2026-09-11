package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

@Source
abstract class MikoRoku : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getCatalog().sortedByDescending { it.rating }.toPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCatalog().sortedByDescending { it.updatedAt }.toPage(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalizeTitle()
        return getCatalog()
            .filter { normalizedQuery in (it.title + it.altTitle).normalizeTitle() }
            .sortedBy { it.title.lowercase() }
            .toPage(page)
    }

    private suspend fun getCatalog(): List<MangaDto> = coroutineScope {
        val github = async { client.get("${RAW_URL}all-manga.json").parseAs<List<MangaDto>>() }
        val summary = async {
            client.get("${STORE_URL}meta/mangaSummary", ensureSuccess = false).use { response ->
                // The site's Firestore quota can run out while GitHub and Blogger remain available.
                if (response.code == 429) return@use emptyList()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.parseAs<FirestoreDocument<SummaryFields>>().fields.list.values
            }
        }
        mergeCatalog(github.await(), summary.await())
    }

    private fun List<MangaDto>.toPage(page: Int): MangasPage = MangasPage(
        drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).map { it.toSManga(baseUrl) },
        page.toLong() * PAGE_SIZE < size,
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.queryParameter("slug") ?: return null
        return getCatalog().find { it.slug == slug }?.toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = (baseUrl + manga.url).toHttpUrl().queryParameter("slug")
        // Old library entries still have Blogger paths; the catalog supplies their new slug.
        val entry = getCatalog().first {
            if (slug != null) it.slug == slug else it.title.normalizeTitle() == manga.title.normalizeTitle()
        }
        val details = async {
            if (fetchDetails) getDetails(entry).toSManga(baseUrl).apply { url = manga.url } else manga
        }
        val updatedChapters = async {
            if (fetchChapters) getChapters(entry) else chapters
        }
        SMangaUpdate(details.await(), updatedChapters.await())
    }

    private suspend fun getDetails(entry: MangaDto): MangaDto {
        val url = "${STORE_URL}manga/${entry.slug}".toHttpUrl().newBuilder()
        DETAIL_FIELDS.forEach { url.addQueryParameter("mask.fieldPaths", it) }
        return client.get(url.build(), ensureSuccess = false).use { response ->
            if (response.code == 404 || response.code == 429) return@use entry
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val fields = response.parseAs<FirestoreDocument<MangaFields>>().fields
            check(!fields.isDraft) { "Manga is not published" }
            fields.toManga(entry.slug, entry)
        }
    }

    private suspend fun getChapters(entry: MangaDto): List<SChapter> {
        val firestore = getFirestoreChapters(entry.slug)
        val blogger = try {
            getBloggerChapters(entry.title, BLOGGER_URL)
        } catch (e: IOException) {
            if (firestore.isEmpty()) throw e
            emptyList()
        }
        val chapters = (firestore + blogger).ifEmpty {
            getBloggerChapters(entry.title, MIRROR_URL)
        }
        return chapters.distinctBy {
            if (it.chapter_number >= 0) it.chapter_number.toString() else it.url
        }.sortedByDescending { it.chapter_number }
    }

    private suspend fun getFirestoreChapters(slug: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var pageToken: String? = null
        do {
            val url = "${STORE_URL}manga/$slug/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "100")
            CHAPTER_FIELDS.forEach { url.addQueryParameter("mask.fieldPaths", it) }
            pageToken?.let { url.addQueryParameter("pageToken", it) }
            val result = client.get(url.build(), ensureSuccess = false).use { response ->
                if (response.code == 429) return emptyList()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.parseAs<FirestoreList<ChapterFields>>()
            }
            chapters += result.documents.filterNot { it.fields.isDraft }.map { it.toSChapter(slug, baseUrl) }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return chapters
    }

    private suspend fun getBloggerChapters(title: String, host: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var startIndex = 1
        do {
            val url = "$host/feeds/posts/default".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "json")
                .addQueryParameter("max-results", "150")
                .addQueryParameter("start-index", startIndex.toString())
                .addQueryParameter("q", title)
                .build()
            val entries = client.get(url).parseAs<BloggerResponse>().feed.entry
            chapters += entries.filter { it.isChapterOf(title) }.map { it.toSChapter(host == MIRROR_URL) }
            startIndex += entries.size
        } while (entries.size == 150)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val slug = url.queryParameter("slug")
        val id = url.queryParameter("chapter")
        val images = if (slug != null && id != null) {
            val chapterUrl = "${STORE_URL}manga/$slug/chapters".toHttpUrl().newBuilder()
                .addPathSegment(id)
                .build()
            val fields = client.get(chapterUrl).parseAs<FirestoreDocument<ChapterFields>>().fields
            if (fields.isDraft) return emptyList()
            fields.pageUrls(baseUrl)
        } else {
            client.get(getChapterUrl(chapter)).asJsoup()
                .select(".post-body img, .entry-content img")
                .mapNotNull { it.imageUrl() }
        }
        return images.mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("/manga-reader")) return baseUrl + chapter.url
        val host = if (chapter.url.endsWith("#sv2")) MIRROR_URL else BLOGGER_URL
        return host + chapter.url.substringBefore('#')
    }

    companion object {
        private const val PAGE_SIZE = 30
        internal const val RAW_URL = "https://raw.githubusercontent.com/moemaomao/mymangadata/main/"
        private const val STORE_URL = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/"
        private const val BLOGGER_URL = "https://www.mikodrive.my.id"
        private const val MIRROR_URL = "https://www.yomidays.my.id"
        private val DETAIL_FIELDS = listOf("title", "altTitle", "img", "cover", "desc", "genres", "author", "artist", "status", "type", "isDraft")
        private val CHAPTER_FIELDS = listOf("title", "num", "date", "createdAt", "isDraft")
    }
}
