package eu.kanade.tachiyomi.extension.en.duskscans

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@Source
class DuskScans(
    override val lang: String = "en",
    override val id: Long = 1234567890123456789,
) : KeiSource() {

    override val name = "Dusk Scans"
    override val baseUrl = "https://duskscans.com"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$apiUrl/manga")
        val mangas = response.parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$apiUrl/manga")
        val mangas = response.parseAs<List<MangaDto>>()
        val sorted = mangas.sortedByDescending { it.updatedAt }
        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get("$apiUrl/manga")
        val mangas = response.parseAs<List<MangaDto>>()
        val filtered = if (query.isNotBlank()) {
            mangas.filter { it.title.contains(query, ignoreCase = true) }
        } else {
            mangas
        }
        return MangasPage(filtered.map { it.toSManga() }, false)
    }

    // ============================== Details ================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.lastOrNull() ?: return null
        val response = client.get("$apiUrl/manga/$slug")
        val manga = response.parseAs<MangaDto>()
        return manga.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get("$apiUrl/manga/${manga.url}")
        val mangaDto = response.parseAs<MangaDto>()
        return mangaDto.toSManga()
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/")
        val mangaSlug = parts[0]
        val chapterNum = parts[1]
        return "$baseUrl/manga/$mangaSlug/chapter/$chapterNum"
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val response = client.get("$apiUrl/manga/${manga.url}")
        val mangaDto = response.parseAs<MangaDto>()
        return mangaDto.chapters.map { chapter ->
            SChapter.create().apply {
                url = "${mangaDto.slug}/${chapter.id}"
                name = if (chapter.title.isNotBlank()) {
                    "Ch. ${chapter.number} - ${chapter.title}"
                } else {
                    "Chapter ${chapter.number}"
                }
                chapter_number = chapter.number.toFloat()
                date_upload = parseDate(chapter.releaseDate)
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ============================== Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val response = client.get("$apiUrl/chapter/$chapterId")
        val chapterDetail = response.parseAs<ChapterDetailDto>()
        val pages = Json.parseToJsonElement(chapterDetail.pages)
            .let { jsonElement ->
                val arr = jsonElement as kotlinx.serialization.json.JsonArray
                arr.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            }
        return pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Helpers ================================

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@toSManga.title
        thumbnail_url = cover
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description
        genre = genres.joinToString(", ")
        status = when (this@toSManga.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return runCatching {
            java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
