package eu.kanade.tachiyomi.extension.en.leslievictims

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class LeslieAndVictims : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = fetchLibrary().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchLibrary()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = (baseUrl + manga.url).toHttpUrl().queryParameter("series")
            ?: throw Exception("Invalid manga URL")
        val entry = findEntry(seriesId)
        return SMangaUpdate(entry.toSManga(baseUrl), entry.getChapters(baseUrl))
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val seriesId = url.queryParameter("series")
            ?: throw Exception("Missing series ID in chapter URL")
        val chId = url.queryParameter("ch")
            ?: throw Exception("Missing chapter ID in chapter URL")

        val chapterRoot = findEntry(seriesId).getChapterRoot(chId)

        if (chapterRoot != null) {
            val rootUrl = chapterRoot.url
            return when (chapterRoot.mode) {
                "list" ->
                    chapterRoot.data.parseAs<List<String>>()
                        .mapIndexed { i, file -> Page(i, imageUrl = "$rootUrl/$file") }
                "count" -> {
                    val count = chapterRoot.data.parseAs<Int>()
                    (1..count).map { i ->
                        Page(i - 1, imageUrl = "$rootUrl/${i.toString().padStart(2, '0')}.webp")
                    }
                }
                else -> emptyList()
            }
        }

        val baseImgUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("content")
            .addPathSegment(seriesId)
            .addPathSegment(chId)
            .build()

        val pages = mutableListOf<Page>()
        var pageNum = 1
        while (pageNum <= 150) {
            val imgUrl = baseImgUrl.newBuilder()
                .addPathSegment("${pageNum.toString().padStart(2, '0')}.webp")
                .build()

            val (isSuccess, contentType) = client.head(imgUrl, ensureSuccess = false).use { res ->
                res.isSuccessful to (res.header("Content-Type") ?: "")
            }

            if (isSuccess && contentType.startsWith("image")) {
                pages.add(Page(pageNum - 1, imageUrl = imgUrl.toString()))
                pageNum++
            } else {
                break
            }
        }

        return pages
    }

    // ============================== Utilities =============================

    private suspend fun fetchLibrary(): List<LibraryEntry> = client.get("$baseUrl/manga.json").parseAs()

    private suspend fun findEntry(seriesId: String): LibraryEntry = fetchLibrary()
        .find { it.getId() == seriesId }
        ?: throw Exception("Series not found: $seriesId")
}
