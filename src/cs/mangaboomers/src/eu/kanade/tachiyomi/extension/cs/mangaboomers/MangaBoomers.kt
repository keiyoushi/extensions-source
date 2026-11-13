package eu.kanade.tachiyomi.extension.cs.mangaboomers

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody

@Source
abstract class MangaBoomers : KeiSource() {

    override val supportsFilterFetching = true

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    private fun api(endpoint: String): HttpUrl.Builder = baseHttpUrl.newBuilder()
        .addPathSegment("api")
        .addPathSegment(endpoint)

    private suspend inline fun <reified T> postApi(endpoint: String, body: RequestBody): T = client.post(api(endpoint).build(), Fetch.XHR.from(READER_PATH), body).parseAs()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = fetchList("popularmangalist")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchList("newmangalist")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected
        val state = filters.firstInstanceOrNull<StateFilter>()?.selected
        val search = query.trim()

        val mangas = postApi<SearchDto>("searchManga", SearchBody(genres.map(::GenreIdDto)).toJsonRequestBody()).mangas
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
            // The API ignores the type and state filters, so they are applied here
            .filter { type == null || it.type?.trim() == type }
            .filter { state == null || it.translateState?.trim() == state }
            .map { it.toSManga(baseHttpUrl) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host || url.pathSegments.firstOrNull() != "read") return null
        val id = url.queryParameter("m")?.toIntOrNull() ?: return null
        return getDetails(id)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl$READER_PATH?m=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga.url.toInt()) else manga }
        val chapterList = async { if (fetchChapters) getChapters(manga.url) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl$READER_PATH?m=${chapter.mangaId}&c=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = api("pagedata")
            .addQueryParameter("mangaId", chapter.mangaId)
            .addQueryParameter("chapterId", chapter.url)
            .build()
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter))).parseAs<PagesDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = baseHttpUrl.resolve(page.image)!!.toString())
        }
    }

    override suspend fun fetchFilterData(): JsonElement = postApi<OptionsDto>("optionsForSearch", EmptyBody().toJsonRequestBody()).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val options = data?.parseAs<OptionsDto>() ?: return FilterList()
        return FilterList(
            GenreFilter(options.tags),
            TypeFilter(options.types),
            StateFilter(options.states),
        )
    }

    private suspend fun fetchList(endpoint: String): MangasPage {
        val mangas = postApi<List<MangaDto>>(endpoint, EmptyBody().toJsonRequestBody())
        return MangasPage(mangas.map { it.toSManga(baseHttpUrl) }, false)
    }

    private suspend fun getDetails(id: Int): SManga = postApi<MangaInfoDto>("mangaInfo", MangaIdBody(id).toJsonRequestBody()).toSManga(baseHttpUrl)

    private suspend fun getChapters(mangaId: String): List<SChapter> {
        val url = api("chapterdata").addQueryParameter("mangaId", mangaId).build()
        return client.get(url, Fetch.XHR.from("$READER_PATH?m=$mangaId")).parseAs<List<ChapterGroupDto>>()
            .flatMap { it.chapters }
            .map { it.toSChapter(mangaId) }
            .reversed()
    }

    private val SChapter.mangaId: String get() = memo[MANGA_ID]!!.stringOrNull!!

    companion object {
        private const val READER_PATH = "/read"
    }
}
