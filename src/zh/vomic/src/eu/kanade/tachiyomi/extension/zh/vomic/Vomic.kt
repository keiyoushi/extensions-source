package eu.kanade.tachiyomi.extension.zh.vomic

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Vomic :
    KeiSource(),
    ConfigurableSource {

    private val baseHost get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = "https://api.$baseHost/pics_new/pics"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(SignInterceptor(apiUrl.toHttpUrl().host))
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("user-agent", "Dart/3.13 (dart:io)")
        set("platform", "android")
        set("hide-content", "0")
        set("name", "pics")
        set("version", "1.2.1")
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(1)))

    // Latest
    override suspend fun getLatestUpdates(page: Int) = client.get(
        "$apiUrl/c/getUpdateBook".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", "$page")
            addQueryParameter("limit", "$UPDATE_LIMIT")
        }.build(),
    ).parse<List<Manga>>().let {
        MangasPage(it.map { it.toSManga() }, it.size >= UPDATE_LIMIT)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val limit = if (query.isEmpty()) MANGA_LIMIT else SEARCH_LIMIT
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("c/getCartoonList")
            addQueryParameter("page", "$page")
            addQueryParameter("limit", "$limit")

            if (query.isNotEmpty()) {
                addQueryParameter("keyword", query)
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is CategoryFilter -> addQueryParameter("cat_id", filter.toUriPart())
                        is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                        is StatusFilter -> addQueryParameter("process", filter.toUriPart())
                        is CreativityFilter -> addQueryParameter("creativity", filter.toUriPart())
                        is RegionFilter -> addQueryParameter("region", filter.toUriPart())
                        is DisplayTypeFilter -> addQueryParameter("dis_type", filter.toUriPart())
                        else -> {}
                    }
                }
            }
        }.build()

        val mangas = client.get(url).parse<List<Manga>>().map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= limit)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val mangaId = url.pathSegments.getOrNull(1) ?: return null
        mangaId.toIntOrNull() ?: return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = mangaId },
            emptyList(),
            true,
            false,
        ).manga
    }

    // MangaUpdate
    override fun getMangaUrl(manga: SManga) = "$baseUrl/detail/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(
            "$apiUrl/c/getCartoonInfoV1?car_id=${manga.url}",
        ).parse<Manga>()

        return SMangaUpdate(
            details.toSManga(),
            details.chapters.map { it.toSChapter() },
        )
    }

    // Pages
    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, chapterId) = chapter.url.split("/")
        return "$baseUrl/chapter/$mangaId/$chapterId"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/")
        var response = client.get(
            "$apiUrl/c/getChapterInfo?c_id=$chapterId",
            authHeaders,
            ensureSuccess = false,
        )

        if (!response.isSuccessful) {
            response.close()
            login()
            response = client.get(
                "$apiUrl/c/getChapterInfo?c_id=$chapterId",
                authHeaders,
            )
        }

        return response.parse<Pages>().images.mapIndexedNotNull { index, image ->
            image.url?.let { Page(index, imageUrl = it) }
        }
    }

    // Related mangas
    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get("$apiUrl/c/getDetailRecommendList")
        .parse<List<Manga>>().map { it.toSManga() }

    // Filters
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(
        "$apiUrl/c/getCatList",
    ).parse<List<Category>>().associate { it.name to it.id }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            add(CreativityFilter())
            add(RegionFilter())
            add(DisplayTypeFilter())

            data?.parseAs<Map<String, Int>>().orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { add(CategoryFilter(it)) }
        },
    )

    // Preferences
    private val preferences = getPreferences()

    private var token: String
        get() = preferences.getString(PREF_TOKEN, "")!!
        set(value) = preferences.edit().putString(PREF_TOKEN, value).apply()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_EMAIL
            title = "📧 邮箱"
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "🔑 密码"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    // Utils
    private val authHeaders get() = headersBuilder()
        .set("Authorization", "Bearer $token")
        .build()

    private suspend fun login() {
        val email = preferences.getString(PREF_EMAIL, "")!!
        val password = preferences.getString(PREF_PASSWORD, "")!!

        if (email.isEmpty() || password.isEmpty()) {
            throw IOException("请在设置中填写邮箱和密码以访问内容")
        }

        val response = client.post(
            "$apiUrl/login",
            LoginPayload(email, password).toJsonRequestBody(),
        )

        token = response.parseAs<Token>().token
    }

    inline fun <reified T> Response.parse(): T = parseAs<Result<T>>().result

    private companion object {
        const val MANGA_LIMIT = 20
        const val SEARCH_LIMIT = 10
        const val UPDATE_LIMIT = 15
        const val PREF_EMAIL = "email"
        const val PREF_PASSWORD = "password"
        const val PREF_TOKEN = "token"
    }
}
