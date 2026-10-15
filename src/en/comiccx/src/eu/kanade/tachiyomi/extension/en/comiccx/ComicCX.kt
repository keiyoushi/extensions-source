package eu.kanade.tachiyomi.extension.en.comiccx

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.dataimage.DataImageInterceptor
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class ComicCX : KeiSource() {

    private val apiUrl = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(DataImageInterceptor())

    // =============================== MangasPage ===========================
    override suspend fun getPopularManga(page: Int) = getCatalog(page, "popularity")

    override suspend fun getLatestUpdates(page: Int) = getCatalog(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getCatalog(page, query = query)

    private suspend fun getCatalog(
        page: Int,
        sort: String? = null,
        query: String = "",
    ): MangasPage {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .apply {
                sort?.let { addQueryParameter("sort", it) }
                if (query.isNotBlank()) addQueryParameter("search", query)
            }
            .build()

        val data = client.get(url).parseAs<MangaListResponse>()
        val mangas = data.manga.map { it.toSManga(baseUrl) }
        val hasNextPage = (data.pagination?.page ?: 1) < (data.pagination?.pages ?: 1)

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== MangaUpdates =============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url

        val updatedManga = async {
            if (fetchDetails) {
                client.get("$apiUrl/manga/$slug").parseAs<MangaItem>().toSManga(baseUrl)
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$apiUrl/manga/$slug/chapters").parseAs<List<ChapterItem>>()
                    .sortedByDescending { it.chapterNumber }
                    .map { it.toSChapter(slug) }
            } else {
                chapters
            }
        }
        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.substringBefore("/")
        val chapNumStr = if (chapter.chapter_number % 1 == 0f) {
            chapter.chapter_number.toInt().toString()
        } else {
            chapter.chapter_number.toString()
        }
        return "$baseUrl/manga/$slug/reader/$chapNumStr"
    }

    // =============================== Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringBefore("/")
        val id = chapter.url.substringAfter("/").toInt()

        val url = "$apiUrl/manga/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", id.toString())
            .build()

        val chapters = client.get(url).parseAs<List<ChapterItem>>()

        val chapter = chapters.find { it.id == id }
            ?: throw Exception("Chapter not found")
        return chapter.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url.resolveImageUrl())
        }
    }

    // ============================= Utilities ================================

    private fun String?.resolveImageUrl(): String {
        if (this.isNullOrBlank()) return ""
        return when {
            startsWith("/") -> "$baseUrl$this"
            else -> this
        }
    }
}
