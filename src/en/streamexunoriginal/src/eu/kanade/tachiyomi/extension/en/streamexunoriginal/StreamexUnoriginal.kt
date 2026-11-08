package eu.kanade.tachiyomi.extension.en.streamexunoriginal

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class StreamexUnoriginal : KeiSource() {

    override val supportsLatest = false

    private val apiUrl get() = "$baseUrl/api/manga"

    override suspend fun getPopularManga(page: Int) = getList("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getList(filters.firstInstance<ListFilter>().value, page)
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs<ListDto>().toMangasPage()
    }

    private suspend fun getList(list: String, page: Int) = client.get("$apiUrl/$list?page=$page", Fetch.XHR).parseAs<ListDto>().toMangasPage()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.firstOrNull() != "manga") return null
        val id = segments.getOrNull(if (segments.getOrNull(1) == "read") 2 else 1)?.toIntOrNull() ?: return null
        return getDetails(id.toString())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga.url) else manga }
        val chapterList = async {
            if (fetchChapters) {
                client.get("$apiUrl/chapters?id=${manga.url}", Fetch.XHR).parseAs<ChaptersDto>().chapters.map { it.toSChapter(manga.url) }
            } else {
                chapters
            }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getDetails(id: String) = client.get("$apiUrl/info/$id", Fetch.XHR).parseAs<MediaDto>().toSManga()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().queryParameter("chapterId")!!
        val url = "$apiUrl/pages".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapterId)
            .build()
        // The site shows the pages through its image proxy
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter))).parseAs<List<PageDto>>().mapIndexed { index, page ->
            val image = "$baseUrl/api/manga-image".toHttpUrl().newBuilder()
                .addQueryParameter("url", page.img)
                .build()
            Page(index, imageUrl = image.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
