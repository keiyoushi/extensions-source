package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.RequestBody.Companion.toRequestBody

@Source
abstract class Dilar : KeiSource() {
    private val ecies = Ecies()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        add("X-DH-Pub", ecies.clientPubB64)
        add("X-Crypto-Caps", "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15")
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/rankings")
        val data = response.parseAs<RankingsDto>()
        val entries = data.topSeries
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/series?page=$page")
        val data = response.parseAs<SeriesListDto>()
        val entries = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, data.hasNextPage)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = SearchRequestDto(query, page).toJsonRequestBody()
        val response = client.post("$baseUrl/api/search/filter", body)
        val data = response.parseAs<SearchListDto>()
        val entries = data.rows.filterNot { it.isNovel() }
            .map { it.toSManga() }

        return MangasPage(entries, data.hasNextPage)
    }

    // Details & Chapters

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (!fetchDetails) return@async manga
            client.get("$baseUrl/api/series/${manga.getMangaId()}")
                .parseAs<SeriesDto>()
                .toSManga()
        }

        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val response = client.get("$baseUrl/api/series/${manga.getMangaId()}/chapters")
            response.parseAs<ChapterListDto>().chapters.flatMap { chapter ->
                chapter.releases.map { it.toSChapter(chapter, manga.url) }
            }
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl/api/chapters/${chapter.url.substringAfterLast("#")}"
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val unlock = client.post("$chapterUrl/unlock/free", body).parseAs<UnlockDto>()
        val chapterHeaders = headers.newBuilder().set("X-Unlock-Free-Chapter", unlock.token).build()
        val encrypted = client.get(chapterUrl, chapterHeaders).parseAs<EncryptedResponseDto>()

        val data = ecies.decrypt(encrypted).parseAs<PageListDto>()
        return data.pages.sortedBy { it.order }
            .mapIndexed { index, page ->
                Page(index, imageUrl = "$baseUrl/uploads/releases/${data.storageKey}/hq/${page.url}")
            }
    }

    // common

    private fun SManga.getMangaId(): String = this.url.substringBeforeLast("/")

    fun createThumbnail(mangaId: String, cover: String): String {
        val thumbnail = "large_${cover.substringBeforeLast(".")}.webp"

        return "$baseUrl/uploads/manga/cover/$mangaId/$thumbnail"
    }
}
