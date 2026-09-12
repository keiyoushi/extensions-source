package eu.kanade.tachiyomi.extension.all.mangamillion

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

@Source
abstract class MangaMillion : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/api"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()
    private val serviceLang: String
        get() = if (lang in SERVICE_LANGUAGES) lang else "en"

    private val token: String
        get() = preferences.getString(TOKEN_PREF_KEY, "")!!

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor {
            val request = it.request()
            if (request.url.host != "api.$domain" || request.url.pathSegments.last() == "register") return@addInterceptor it.proceed(request)

            val usedToken = request.header("Access-Token").orEmpty()
            if (usedToken.isNotEmpty()) {
                val response = it.proceed(request)
                if (response.code != 403) return@addInterceptor response
                response.close()
            }

            val newToken = runBlocking { getToken(rejected = usedToken) }
            it.proceed(request.newBuilder().header("Access-Token", newToken).build())
        }
    }

    override fun Headers.Builder.configureHeaders() = apply {
        if (token.isNotEmpty()) set("Access-Token", token)
        set("Accept", "*/*")
        set("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    private suspend fun getToken(rejected: String): String = tokenMutex.withLock {
        token.takeIf { it.isNotEmpty() && it != rejected }?.let { return it }

        val url = "$apiUrl/register".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .build()

        client.post(url, EMPTY_BODY).parseAsProto<TokenResponse>().token.accessToken
            .also { preferences.edit().putString(TOKEN_PREF_KEY, it).apply() }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/manga_list".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .build()

        val result = client.get(url).parseAsProto<SeriesResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .sortedByDescending { it.series.views }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/manga_list".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .build()

        val result = client.get(url).parseAsProto<SeriesResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .sortedByDescending { it.series.uploadTime }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagIds = filters.filterIsInstance<TagIdGroup>().flatMap { it.checkedIds }
        val ratingIds = filters.firstInstanceOrNull<RatingFilter>()?.checkedIds.orEmpty()
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .addQueryParameter("translated_language", lang)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("text", query)
                }
                if (tagIds.isNotEmpty()) {
                    addQueryParameter("tag_id", tagIds.joinToString(","))
                }
                if (ratingIds.isNotEmpty()) {
                    addQueryParameter("rating_id", ratingIds.joinToString(","))
                }
            }
            .build()

        val result = client.get(url).parseAsProto<SearchResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/search_parameter".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .build()

        return client.get(url).parseAsProto<SearchParameterResponse>().searchParameter.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val result = data?.parseAs<SearchParameter>() ?: return FilterList()

        return FilterList(
            buildList {
                if (result.genres.isNotEmpty()) add(GenreFilter(result.genres))
                if (result.themes.isNotEmpty()) add(ThemeFilter(result.themes))
                if (result.highlights.isNotEmpty()) add(HighlightsFilter(result.highlights))
                if (result.ratings.isNotEmpty()) add(RatingFilter(result.ratings))
            },
        )
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangas = async {
            if (!fetchDetails) return@async manga
            val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
                .addQueryParameter("service_language", serviceLang)
                .addQueryParameter("avif_enable", "true")
                .addQueryParameter("original_title_id", manga.url)
                .build()
            client.get(url).parseAsProto<DetailsResponse>().detailsEntry.details.toSManga()
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val url = "$apiUrl/chapter_list".toHttpUrl().newBuilder()
                .addQueryParameter("service_language", serviceLang)
                .addQueryParameter("avif_enable", "true")
                .addQueryParameter("original_title_id", manga.url)
                .addQueryParameter("translated_language", lang)
                .build()

            client.get(url).parseAsProto<ChapterResponse>().chapterEntry.chapterGroups
                .flatMap { it.chapterList }
                .filter { it.isAvailable }
                .map { it.toSChapter(manga.url) }
                .fixExtraChapterNumbers()
                .reversed()
        }

        SMangaUpdate(
            mangas.await(),
            chapterList.await(),
        )
    }

    private fun List<SChapter>.fixExtraChapterNumbers(): List<SChapter> = apply {
        for (i in indices) {
            val chapter = this[i]
            if (chapter.chapter_number == -1F) {
                chapter.chapter_number = when {
                    i > 0 -> this[i - 1].chapter_number + 0.01F
                    size > 1 -> this[1].chapter_number - 0.01F
                    else -> 0F
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .addQueryParameter("translated_chapter_id", chapter.url)
            .addQueryParameter("quality", "middle")
            .build()

        val viewer = client.get(url).parseAsProto<ViewerResponse>().viewer
        return viewer.pageList.mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl + "#${viewer.key}:${viewer.iv}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$serviceLang/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$serviceLang/title/${chapter.memo["titleId"]!!.string}/chapter/${chapter.url}"

    companion object {
        private const val TOKEN_PREF_KEY = "access_token"
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val SERVICE_LANGUAGES = setOf(
            "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko-KR", "pt-BR", "ru", "th", "vi", "zh-CN",
        )
    }
}
