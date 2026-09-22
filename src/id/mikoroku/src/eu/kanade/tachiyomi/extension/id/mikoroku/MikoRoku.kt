package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
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
                // The site's Firestore quota can run out while GitHub remains available.
                if (response.code == 429) return@use emptyList()
                if (!response.isSuccessful) throw HttpException(response.code)
                response.parseAs<FirestoreDocument<SummaryFields>>().fields.list.values
            }
        }
        mergeCatalog(github.await(), summary.await())
    }

    private fun List<MangaDto>.toPage(page: Int): MangasPage = MangasPage(
        drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).map { it.toSManga() },
        page.toLong() * PAGE_SIZE < size,
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.queryParameter("slug") ?: return null
        return getCatalog().find { it.slug == slug }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val entry = getCatalog().first { it.slug == manga.url }
        val details = async {
            if (fetchDetails) getDetails(entry).toSManga().apply { url = manga.url } else manga
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
            if (!response.isSuccessful) throw HttpException(response.code)
            val fields = response.parseAs<FirestoreDocument<MangaFields>>().fields
            check(!fields.isDraft) { "Manga is not published" }
            fields.toManga(entry.slug, entry)
        }
    }

    private suspend fun getChapters(entry: MangaDto): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var pageToken: String? = null
        do {
            val url = "${STORE_URL}manga/${entry.slug}/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "100")
            CHAPTER_FIELDS.forEach { url.addQueryParameter("mask.fieldPaths", it) }
            pageToken?.let { url.addQueryParameter("pageToken", it) }
            val result = client.get(url.build()).parseAs<FirestoreList<ChapterFields>>()
            chapters += result.documents.filterNot { it.fields.isDraft }.map { it.toSChapter(entry.slug, baseUrl) }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return chapters.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val slug = url.queryParameter("slug") ?: throw IOException("Invalid chapter url")
        val id = url.queryParameter("chapter") ?: throw IOException("Invalid chapter url")
        val chapterUrl = "${STORE_URL}manga/$slug/chapters/$id".toHttpUrl()
        val fields = client.get(chapterUrl).parseAs<FirestoreDocument<ChapterFields>>().fields
        if (fields.isDraft) return emptyList()
        return fields.pageUrls(baseUrl).mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    companion object {
        private const val PAGE_SIZE = 30
        internal const val RAW_URL = "https://raw.githubusercontent.com/moemaomao/mymangadata/main/"
        private const val STORE_URL = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/"
        private val DETAIL_FIELDS = listOf("title", "altTitle", "img", "cover", "desc", "genres", "author", "artist", "status", "type", "isDraft")
        private val CHAPTER_FIELDS = listOf("title", "num", "date", "createdAt", "isDraft")
    }
}
