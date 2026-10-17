package eu.kanade.tachiyomi.extension.en.coolmic

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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Coolmic :
    KeiSource(),
    ConfigurableSource {

    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "$baseUrl/api/v1"
    private val cdnUrl get() = "https://en-img.$domain"
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("is_mature" to "true")
        addInterceptor(ImageInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 3 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value

        val url = "$apiUrl/search_titles".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per", SEARCH_SIZE.toString())
            .addQueryParameter("search_field", "all")
            .addQueryParameter("sort", sort)
            .apply {
                if (!status.isNullOrEmpty()) {
                    val (name, number) = status.split(":")
                    addQueryParameter("status_filters[0][field]", name)
                    addQueryParameter("status_filters[0][value]", number)
                }
            }.build()

        val result = client.get(url).parseAs<SeriesResponse>()
        val mangas = result.results.map { it.toSManga(cdnUrl) }
        val hasNextPage = page * SEARCH_SIZE < result.total
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.get(getMangaUrl(manga)).parsePageObjects()

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val updatedChapters = result.episodes
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
            .reversed()

        return SMangaUpdate(
            result.title.toSManga().apply { url = manga.url },
            updatedChapters,
        )
    }

    private fun Response.parsePageObjects(): DetailsResponse = asJsoup().selectFirst("title-page")!!.attr(":page-objects").parseAs()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$apiUrl/viewer/comic/secure_episodes/${chapter.url}").parseAs<ViewerResponse>()
        if (result.imageData.isNullOrEmpty()) throw Exception("Log in via WebView and purchase this chapter to read.")
        return result.imageData.map {
            Page(it.num - 1, it.path)
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val response = client.get(page.url)
        val url = response.request.url
        val result = response.parseAs<PageResponse>()
        val key = fetchKey(result.kmsEncryptedDataKey, result.fileName)
        return url.newBuilder().fragment("key=$key").build().toString()
    }

    private suspend fun fetchKey(encryptedKey: String, fileName: String): String {
        var response = requestKey(encryptedKey, fileName, csrfToken())
        if (!response.isSuccessful) {
            response.close()
            response = requestKey(encryptedKey, fileName, csrfToken(refresh = true))
        }
        return response.parseAs<KeyResponse>().decryptedKey
    }

    private suspend fun requestKey(encryptedKey: String, fileName: String, token: String): Response {
        val newHeaders = headers.newBuilder()
            .set("X-CSRF-TOKEN", token)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        return client.post(
            "$apiUrl/decryption_keys",
            newHeaders,
            KeyRequestBody(encryptedKey, fileName).toJsonRequestBody(),
            ensureSuccess = false,
        )
    }

    private var cachedCsrfToken: String? = null

    private suspend fun csrfToken(refresh: Boolean = false): String {
        if (refresh) cachedCsrfToken = null
        return cachedCsrfToken ?: client.get(baseUrl, CacheControl.FORCE_NETWORK).asJsoup()
            .selectFirst("meta[name=csrf-token]")!!
            .attr("content")
            .also { cachedCsrfToken = it }
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
        private const val SEARCH_SIZE = 20
    }
}
