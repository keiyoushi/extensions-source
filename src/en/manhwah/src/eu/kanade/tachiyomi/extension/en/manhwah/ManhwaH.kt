package eu.kanade.tachiyomi.extension.en.manhwah

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class ManhwaH :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::backupImageInterceptor)

    override suspend fun getPopularManga(page: Int) = searchComics(page, "popular")

    override suspend fun getLatestUpdates(page: Int) = searchComics(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchComics(
        page,
        filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular",
        query,
        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id },
    )

    private suspend fun searchComics(page: Int, sort: String, query: String = "", genres: List<String> = emptyList()): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            addQueryParameter("sort", sort)
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genres", genres.joinToString(","))
        }.build()
        val result = client.get(url, Fetch.XHR).parseAs<ListResponseDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.meta.page < result.meta.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return when (url.pathSegments[0]) {
            "title-detail" -> fetchComic(id).toSManga().apply { initialized = true }
            "chapter-detail" -> fetchChapter(id).toSManga()
            else -> null
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title-detail/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = fetchComic(manga.url)
        val showRaws = when (preferences.getString(PREF_RAW_CHAPTERS, RAWS_WITHOUT_TRANSLATIONS)) {
            RAWS_ALWAYS -> true
            RAWS_NEVER -> false
            else -> comic.chapters.isEmpty()
        }
        val chapterList = comic.chapters.map { it.toSChapter(raw = false) } +
            if (showRaws) comic.rawChapters.map { it.toSChapter(raw = true) } else emptyList()
        return SMangaUpdate(comic.toSManga(), chapterList.sortedByDescending { it.chapter_number })
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = fetchComic(manga.url).relatedComics.map { it.toSManga() }

    private suspend fun fetchComic(id: String) = client.get("$baseUrl/api/comics/$id", Fetch.XHR)
        .parseAs<ResponseDto<ComicDetailDto>>().data

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter-detail/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchChapter(chapter.url).pages
        .sortedBy { it.pageOrder }
        .mapIndexed { index, page ->
            val imageUrl = page.imageUrl.toHttpUrl().newBuilder().fragment(page.backupUrl?.toHttpUrl()?.host).build()
            Page(index, imageUrl = imageUrl.toString())
        }

    private suspend fun fetchChapter(id: String) = client.get("$baseUrl/api/chapters/$id", Fetch.XHR).parseAs<ResponseDto<ReaderDto>>().data

    // The site's reader loads a page from its backup host when the first host fails
    private fun backupImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val backupHost = request.url.fragment ?: return chain.proceed(request)
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            null
        }
        if (response?.isSuccessful == true) return response
        response?.close()
        return chain.proceed(request.newBuilder().url(request.url.newBuilder().host(backupHost).fragment(null).build()).build())
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres", Fetch.XHR)
        .parseAs<ResponseDto<List<GenreDto>>>().data.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            data?.parseAs<List<GenreDto>>()?.let(::GenreFilter),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_RAW_CHAPTERS
            title = "Raw chapters"
            entries = arrayOf("Only for titles without translated chapters, like the site", "Always, labelled Raw", "Never")
            entryValues = arrayOf(RAWS_WITHOUT_TRANSLATIONS, RAWS_ALWAYS, RAWS_NEVER)
            summary = "%s"
            setDefaultValue(RAWS_WITHOUT_TRANSLATIONS)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_RAW_CHAPTERS = "pref_raw_chapters"
        private const val RAWS_WITHOUT_TRANSLATIONS = "untranslated"
        private const val RAWS_ALWAYS = "always"
        private const val RAWS_NEVER = "never"
    }
}
