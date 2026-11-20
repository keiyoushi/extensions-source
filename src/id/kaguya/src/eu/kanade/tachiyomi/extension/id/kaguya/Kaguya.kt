package eu.kanade.tachiyomi.extension.id.kaguya

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
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.decodeHex
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

const val CDN_URL = "https://storage.kaguyacdn.my.id"
private const val API_URL = "https://tsuki.kaguya.re/api/v1"

@Source
abstract class Kaguya : KeiSource() {

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambleInterceptor())

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = getComicList(comicsUrl(page).addQueryParameter("sort", "popular").build())

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = getComicList(comicsUrl(page).addQueryParameter("sort", "latest").build())

    // ============================== Search ===============================

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
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "comic") return null
        val slug = url.pathSegments.getOrNull(1)?.ifEmpty { null } ?: return null
        return getComic(slug).toSManga()
    }

    private fun comicsUrl(page: Int) = "$API_URL/comics".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", "24")

    private suspend fun getComicList(url: HttpUrl): MangasPage {
        val dto = client.get(url, Fetch.XHR).parseAs<ResponseDto<ComicListDto>>().data
        return MangasPage(dto.data.map(ComicDto::toSManga), dto.hasNextPage)
    }

    // =========================== Manga Update ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val comic = getComic(slug)
        return SMangaUpdate(comic.toSManga(), comic.toSChapters())
    }

    private suspend fun getComic(slug: String) = client.get("$API_URL/comics/$slug", Fetch.XHR).parseAs<ResponseDto<ComicDto>>().data

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val chapterUrl = getChapterUrl(chapter)
        val ticket = client.get(chapterUrl, Fetch.DOCUMENT)
            .extractNextJs<ReaderPropsDto> { it is JsonObject && "readerTicket" in it && "initialChapter" in it }
            ?.readerTicket
            ?: error("Reader ticket not found")
        // Without the page's reader ticket the site answers with placeholder pages
        val readerHeaders = headers.newBuilder().set("x-reader-ticket", ticket).build()
        val dto = client.get("$baseUrl/api/reader/$chapterId/pages", readerHeaders, Fetch.XHR.from(chapterUrl))
            .parseAs<ResponseDto<ReaderDto>>()
            .data
        if (dto.locked) error("This chapter is locked")

        return dto.pages.sortedBy(PageDto::order).mapIndexed { index, page ->
            val imageUrl = "$API_URL/media/chapter/deliver".toHttpUrl().newBuilder()
                .addQueryParameter("token", page.token)
                .fragment(page.scrambleSeed?.let { decryptSeed(it, dto.readerTicket!!, chapterId) })
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    // Scramble seed: hex "iv.ciphertext.tag", AES-GCM keyed on the pages response's reader ticket and the chapter id
    private fun decryptSeed(seed: String, ticket: String, chapterId: String): String {
        val (iv, ciphertext, tag) = seed.split('.').map(String::decodeHex)
        return (ciphertext + tag).aesDecrypt(aesGcm("$ticket:$chapterId".sha256(), iv)).decodeToString()
    }

    // ============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/genres", Fetch.XHR)
        .parseAs<ResponseDto<List<GenreDto>>>()
        .data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<GenreDto>>())
}
