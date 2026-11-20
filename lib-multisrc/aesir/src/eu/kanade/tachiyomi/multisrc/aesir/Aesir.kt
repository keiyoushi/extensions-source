package eu.kanade.tachiyomi.multisrc.aesir

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.decodeHex
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

abstract class Aesir : KeiSource() {

    protected abstract val apiUrl: String

    protected abstract val cdnUrl: String

    protected open val siteHosts get() = setOf(baseUrl.toHttpUrl().host)

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)
        .addInterceptor(ScrambleInterceptor())

    override suspend fun getPopularManga(page: Int) = getComicList(comicsUrl(page).addQueryParameter("sort", "popular").build())

    override suspend fun getLatestUpdates(page: Int) = getComicList(comicsUrl(page).addQueryParameter("sort", "latest").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = comicsUrl(page).apply {
            query.trim().ifEmpty { null }?.let { addQueryParameter("search", it) }
            filters.filterIsInstance<QueryFilter>().forEach { filter ->
                filter.value?.let { addQueryParameter(filter.param, it) }
            }
        }.build()
        return getComicList(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in siteHosts || url.pathSegments.getOrNull(0) != "comic") return null
        val slug = url.pathSegments.getOrNull(1)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = "/comic/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = client.get("$apiUrl/comics/${manga.url.substringAfterLast('/')}", Fetch.XHR)
            .parseAs<ResponseDto<ComicDto>>()
            .data
        return SMangaUpdate(comic.toSManga(cdnUrl), comic.chapters.map { it.toSChapter(chapterUrl(comic.slug, it)) })
    }

    protected open fun chapterUrl(comicSlug: String, chapter: ChapterDto) = "/comic/$comicSlug/chapter/${chapter.id}"

    protected open fun chapterId(chapter: SChapter) = chapter.url.substringAfterLast('/')

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapterId(chapter)
        val chapterUrl = getChapterUrl(chapter)
        val ticket = client.get(chapterUrl, Fetch.DOCUMENT).extractNextJs<ReaderPropsDto>()?.readerTicket
            ?: error("Reader ticket not found")
        // Without the page's reader ticket the site answers with placeholder pages
        val readerHeaders = headers.newBuilder().set("x-reader-ticket", ticket).build()
        val dto = client.get("$baseUrl/api/reader/$chapterId/pages", readerHeaders, Fetch.XHR.from(chapterUrl))
            .parseAs<ResponseDto<ReaderDto>>()
            .data
        if (dto.locked) return emptyList()

        return dto.pages.sortedBy(PageDto::order).mapIndexed { index, page ->
            val imageUrl = "$apiUrl/media/chapter/deliver".toHttpUrl().newBuilder()
                .addQueryParameter("token", page.token)
                .fragment(page.scrambleSeed?.let { decryptSeed(it, ticket, chapterId) })
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    // Scramble seed: hex "iv.ciphertext.tag", AES-GCM keyed on the reader ticket and the chapter id
    private fun decryptSeed(seed: String, ticket: String, chapterId: String): String {
        val (iv, ciphertext, tag) = seed.split('.').map(String::decodeHex)
        return (ciphertext + tag).aesDecrypt(aesGcm("$ticket:$chapterId".sha256(), iv)).decodeToString()
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres", Fetch.XHR)
        .parseAs<ResponseDto<List<GenreDto>>>()
        .data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<GenreDto>>())

    private fun comicsUrl(page: Int) = "$apiUrl/comics".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", "24")

    private suspend fun getComicList(url: HttpUrl): MangasPage {
        val dto = client.get(url, Fetch.XHR).parseAs<ResponseDto<ComicListDto>>().data
        return MangasPage(dto.data.map { it.toSManga(cdnUrl) }, dto.hasNextPage)
    }
}
