package eu.kanade.tachiyomi.extension.en.mangamelon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString.Companion.toByteString

@Source
abstract class Mangamelon : KeiSource() {

    private companion object {
        const val API_BASE = "https://api.mangamelon.com"
        const val PAGE_SIZE = 36
        const val CHAPTER_LIMIT = 1000
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        // Request bodies must include default-valued fields (e.g. includeNsfw), or the
        // server falls back to its own defaults and hides NSFW content.
        private val requestJson = Json { encodeDefaults = true }
    }

    // ================================ Browse ================================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaPage(sort = "popular", page = page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaPage(sort = "latest", page = page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "latest"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value ?: ""
        return fetchMangaPage(search = query, genre = genre, sort = sort, page = page)
    }

    private suspend fun fetchMangaPage(
        search: String = "",
        genre: String = "",
        sort: String,
        page: Int,
    ): MangasPage {
        val skip = (page - 1) * PAGE_SIZE
        val response = api(
            "api/manga/list",
            MangaListRequest(search = search, genre = genre, sort = sort, limit = PAGE_SIZE, skip = skip),
        ).parseAs<MangaListResponse>()
        val mangas = response.list.map { it.toSManga() }
        // `total` is -1 for plain browse but a real count for searches; fall back to
        // the "full page means more" heuristic when it is not usable.
        val hasNextPage = if (response.total > 0) {
            skip + mangas.size < response.total
        } else {
            mangas.size >= PAGE_SIZE
        }
        return MangasPage(mangas, hasNextPage)
    }

    // ================================ Details ================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.url
        return when {
            fetchDetails && fetchChapters -> coroutineScope {
                val details = async { fetchMangaDetails(mangaId) }
                val chapterList = async { fetchChapters(mangaId) }
                SMangaUpdate(details.await(), chapterList.await())
            }
            fetchDetails -> SMangaUpdate(fetchMangaDetails(mangaId), emptyList())
            fetchChapters -> SMangaUpdate(manga, fetchChapters(mangaId))
            else -> SMangaUpdate(manga, emptyList())
        }
    }

    private suspend fun fetchMangaDetails(mangaId: String): SManga {
        val response = api("api/manga/get", MangaGetRequest(target = mangaId)).parseAs<MangaGetResponse>()
        return response.manga.toSManga()
    }

    private suspend fun fetchChapters(mangaId: String): List<SChapter> {
        val response = api(
            "api/chapter/list",
            ChapterListRequest(target = mangaId, limit = CHAPTER_LIMIT),
        ).parseAs<ChapterListResponse>()
        return response.chapters
            .sortedByDescending { it.seq }
            .map { it.toSChapter(mangaId) }
    }

    // ================================ Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val response = api("api/chapter/get", ChapterGetRequest(target = chapterId)).parseAs<ChapterGetResponse>()
        return response.chapter.pages
            .sortedBy { it.seq }
            .mapIndexed { index, page -> Page(index, imageUrl = page.url) }
    }

    // ================================ URLs ================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val mangaId = when {
            segments.size >= 2 && segments[0] == "manga" -> segments[1]
            segments.size >= 3 && segments[0] == "chapter" -> segments[1]
            else -> return null
        }
        return fetchMangaDetails(mangaId)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter/${chapter.url}"

    // ================================ Filters ================================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(SortFilter(), GenreFilter())

    // ================================ API ================================

    private suspend inline fun <reified T> api(path: String, body: T): Response {
        val data = body.toJsonString(requestJson).toByteArray().toByteString().base64()
        val formBody = "data=$data&sessionid=".toRequestBody(FORM_MEDIA_TYPE)
        return client.post("$API_BASE/$path", formBody)
    }
}
