package eu.kanade.tachiyomi.extension.ja.magazinepocket

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest

@Source
abstract class MagazinePocket :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain"
    private val pageLimit = 25
    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = set("X-Manga-Platform", "3")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 400 && request.url.pathSegments.last().contains("viewer")) {
                throw IOException("Log in via WebView and rent or purchase this chapter to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking("30", page)

    private suspend fun getRanking(rankingId: String, page: Int): MangasPage {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("ranking_id", rankingId)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "26")
            .build()

        val titleIds = hashedGet(url).parseAs<RankingApiResponse>().rankingTitleList
            .map { it.id.toString().padStart(5, '0') }

        if (titleIds.isEmpty()) return MangasPage(emptyList(), false)

        val hasNextPage = titleIds.size > pageLimit
        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("title_id_list", titleIds.take(pageLimit).joinToString())
            .build()

        val result = hashedGet(detailsUrl).parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = hashedGet("$apiUrl/web/title/weekly".toHttpUrl()).parseAs<TitleListResponse>()
        val mangas = result.titleList
            .sortedByDescending { it.episodeFreeUpdated }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/web/search/title".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .build()

            return hashedGet(url).toMangasPage()
        }

        val categoryFilter = filters.firstInstance<CategoryFilter>()
        if (categoryFilter.type == "ranking") {
            return getRanking(categoryFilter.value, page)
        }

        val url = "$apiUrl/search/title".toHttpUrl().newBuilder()
            .addQueryParameter("genre_id", categoryFilter.value)
            .addQueryParameter("limit", "99999")
            .build()

        return hashedGet(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }.reversed()
        return MangasPage(mangas, false)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url.substringAfterLast("/")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val titleId = manga.url.substringAfterLast("/") // for old url compatibility
        val title = async {
            val url = "$apiUrl/title/list".toHttpUrl().newBuilder()
                .addQueryParameter("title_id_list", titleId)
                .build()
            hashedGet(url).parseAs<DetailResponse>().titleList.first()
        }

        val details = async {
            if (!fetchDetails) return@async manga
            val result = title.await()
            if (result.genreIdList.isNullOrEmpty()) return@async result.toSManga(null)
            val url = "$apiUrl/genre/list".toHttpUrl().newBuilder()
                .addQueryParameter("genre_id_list", result.genreIdList.joinToString())
                .build()

            val genres = hashedGet(url).parseAs<GenreListResponse>().genreList.joinToString { it.genreName }
            result.toSManga(genres)
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val episodeIds = title.await().episodeIdList
            if (episodeIds.isNullOrEmpty()) return@async emptyList()

            val episodeIdList = episodeIds.joinToString()
            val body = FormBody.Builder()
                .add("episode_id_list", episodeIdList)
                .build()

            client.post("$apiUrl/episode/list", hashedHeaders(mapOf("episode_id_list" to episodeIdList)), body)
                .parseAs<EpisodeListResponse>().episodeList
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/title/${chapter.memo["titleId"]!!.string}/episode/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episode_id", chapter.url)
            .build()

        val result = hashedGet(url).parseAs<ViewerApiResponse>()
        return result.pageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$imageUrl#${result.scrambleSeed}:${result.titleId}:${result.episodeId}")
        }
    }

    private fun generateHash(params: Map<String, String>, birthday: String = "", expires: String = ""): String {
        val paramStrings = params.toSortedMap().map { (key, value) ->
            getHashedParam(key, value)
        }

        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.hash("SHA-256")
        val cookieHash = getHashedParam(birthday, expires)
        val finalString = "$hash1$cookieHash"
        return finalString.hash("SHA-512")
    }

    private fun getHashedParam(key: String, value: String): String {
        val keyHash = key.hash("SHA-256")
        val valueHash = value.hash("SHA-512")
        return "${keyHash}_$valueHash"
    }

    private fun String.hash(algorithm: String): String = MessageDigest.getInstance(algorithm).digest(this.toByteArray()).toHexString()

    private fun hashedHeaders(params: Map<String, String>): Headers = headersBuilder()
        .set("X-Manga-Hash", generateHash(params))
        .build()

    private suspend fun hashedGet(url: HttpUrl): Response {
        val queryParams = url.queryParameterNames.associateWith { url.queryParameter(it)!! }
        return client.get(url, hashedHeaders(queryParams))
    }

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
