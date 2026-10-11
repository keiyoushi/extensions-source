package eu.kanade.tachiyomi.extension.ja.mangaboxme

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.Normalizer

@Source
abstract class MangaBoxMe :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api/honshi/graphql"
    private val preferences by getPreferencesLazy()

    override fun getHomeUrl(): String = "$baseUrl/reader/"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(GraphQLErrorInterceptor())
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 404 && request.url.pathSegments.last() == "images") {
                throw IOException("Log in via WebView and rent or purchase this chapter to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.post(
        apiUrl,
        graphQLBody(
            query = RANKING_QUERY,
            operationName = "Ranking",
        ),
    ).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.post(
        apiUrl,
        graphQLBody(
            query = NEW_ARRIVALS_QUERY,
            operationName = "NewArrivals",
        ),
    ).toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val result = client.post(
            apiUrl,
            graphQLBody(
                query = SEARCH_QUERY,
                operationName = "Search",
            ),
        ).parseGraphQLAs<SearchResponse>()

        val keyword = query.normalize()
        val mangas = result.honshiMangas
            .filter { manga -> (manga.searchKeywords + manga.title).any { it.normalize().contains(keyword) } }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Response.toMangasPage(): MangasPage {
        val mangas = parseGraphQLAs<FeaturedResponse>().featured.sections
            .flatMap { it.items }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/reader/${manga.url}/episodes/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.post(
            "$baseUrl/api/honshi/jsonrpc",
            RpcRequest(
                jsonrpc = "2.0",
                method = "get_all_episodes_by_manga_id",
                params = MangaParams(manga.url, 1),
            ).toJsonRequestBody(),
        ).parseAs<DetailsResponse>().result

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.episodes
            .filter { it.isReleased && (!hideLocked || !it.isLocked) }
            .map { it.toSChapter(manga.url) }
            .reversed()

        return SMangaUpdate(
            result.toSManga(),
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.memo["mangaId"]!!.string}/episodes/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$baseUrl/api/honshi/episode/${chapter.url}/images").parseAs<ImagesResponse>()
        return result.imageUrls.mapIndexed { i, imageUrl ->
            val url = "$baseUrl/api/honshi/image".toHttpUrl().newBuilder()
                .addQueryParameter("d", imageUrl)
                .fragment(result.mask.toString())
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    private fun String.normalize() = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
