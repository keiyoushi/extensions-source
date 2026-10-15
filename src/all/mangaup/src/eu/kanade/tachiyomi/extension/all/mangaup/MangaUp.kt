package eu.kanade.tachiyomi.extension.all.mangaup

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaUp :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = "manga-up.com"
    private val apiUrl get() = "https://global-api.$domain/api"
    private val imgUrl get() = "https://global-img.$domain"
    private val preferences by getPreferencesLazy()
    private val secretMutex = Mutex()
    private val manualSecret: String
        get() = preferences.getString(SECRET_PREF, "")!!

    private var secret: String? = null
    private var rejectedSecret: String? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addCommonParameters()
            .addQueryParameter("lang", lang)
            .build()

        val result = apiRequest(url).parseAsProto<SeriesResponse>()
        val mangas = result.titles.orEmpty()
            .sortedByDescending { it.bookmarks }
            .map { it.toSManga(imgUrl) }

        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addCommonParameters()
            .addQueryParameter("lang", lang)
            .build()

        val result = apiRequest(url).parseAsProto<SeriesResponse>()
        val mangas = result.titles.orEmpty()
            .sortedByDescending { it.updatedAt }
            .map { it.toSManga(imgUrl) }

        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstance<SelectFilter>().value
        if (query.isNotBlank()) {
            val url = "$apiUrl/manga/search".toHttpUrl().newBuilder()
                .addCommonParameters()
                .addQueryParameter("word", query)
                .addQueryParameter("lang", lang)
                .build()

            val result = apiRequest(url).parseAsProto<SearchResponse>()
            val mangas = result.titles?.map { it.toSManga(imgUrl) }.orEmpty()
            return MangasPage(mangas, false)
        }

        if (genre == FAVORITES || genre == HISTORY) {
            if (getSecret() == null) {
                throw IOException("Log in via WebView to access your $genre.")
            }

            val url = "$apiUrl/my_page".toHttpUrl().newBuilder()
                .addCommonParameters()
                .addQueryParameter("lang", lang)
                .build()

            val result = apiRequest(url).parseAsProto<MyPageResponse>()
            val titles = if (genre == FAVORITES) result.favorites else result.history
            val mangas = titles?.map { it.toSManga(imgUrl) }.orEmpty()
            return MangasPage(mangas, false)
        }

        if (genre.isEmpty()) {
            return getPopularManga(page)
        }

        val url = "$apiUrl/manga/tag".toHttpUrl().newBuilder()
            .addCommonParameters()
            .addQueryParameter("tag_id", genre)
            .addQueryParameter("lang", lang)
            .build()

        val result = apiRequest(url).parseAsProto<SearchResponse>()
        val mangas = result.titles?.map { it.toSManga(imgUrl) }.orEmpty()
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = baseUrl.toHttpUrl().host
        if (url.host != host && url.host != "www.$host") throw Exception("Unsupported URL")
        val titleId = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = titleId
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.substringAfterLast("/")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hidePaid = preferences.getBoolean(HIDE_PAID_PREF, false)
        val titleId = manga.url.substringAfterLast("/") // for old url compatibility
        val url = "$apiUrl/manga/detail_v2".toHttpUrl().newBuilder()
            .addCommonParameters()
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("quality", "high")
            .addQueryParameter("ui_lang", lang)
            .build()

        val result = apiRequest(url).parseAsProto<MangaDetailResponse>()
        return SMangaUpdate(
            result.toSManga(titleId, imgUrl),
            result.chapters
                .filter { !hidePaid || it.price == null }
                .map { it.toSChapter(titleId) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo["titleId"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/manga/viewer_v2".toHttpUrl().newBuilder()
            .addCommonParameters()
            .addQueryParameter("chapter_id", chapter.url)
            .addQueryParameter("quality", "high")
            .addQueryParameter("lang", lang)
            .build()

        val result = apiRequest(url, post = true).parseAsProto<ViewerResponse>()

        val pages = result.pageBlocks
            .firstOrNull { it.chapterId == chapter.url.toInt() }
            ?.pages
            ?.filterNot { it.url.contains("tutorial") }

        if (pages.isNullOrEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return pages.mapIndexed { i, page ->
            Page(i, imageUrl = "$imgUrl${page.url}#${page.key}:${page.iv}")
        }
    }

    private suspend fun HttpUrl.Builder.addCommonParameters() = apply {
        addQueryParameter("app_ver", "0")
        addQueryParameter("os_ver", "0")
        getSecret()?.let { addQueryParameter("secret", it) }
    }

    private suspend fun apiRequest(url: HttpUrl, post: Boolean = false): Response {
        val response = if (post) {
            client.post(url, EMPTY_BODY, ensureSuccess = false)
        } else {
            client.get(url, ensureSuccess = false)
        }
        if (response.isSuccessful) return response
        response.close()

        val secret = url.queryParameter("secret")
        if (secret != null) {
            when (response.code) {
                // malformed secret
                500 -> if (secret == manualSecret) throw IOException("Invalid Secret")

                // invalid secret -> login was aborted; UA mismatch from previous login
                401 -> {
                    rejectSecret(secret)
                    val newSecret = getSecret()
                    val retryUrl = url.newBuilder().apply {
                        if (newSecret != null) setQueryParameter("secret", newSecret) else removeAllQueryParameters("secret")
                    }.build()
                    return apiRequest(retryUrl, post)
                }
            }
        }
        throw HttpException(response.code)
    }

    private suspend fun getSecret(): String? = secretMutex.withLock {
        val manual = manualSecret
        if (manual.isNotBlank()) return manual.takeIf { it != rejectedSecret }

        secret ?: getLocalStorage(baseUrl, "secret")
            ?.takeIf { it.isNotBlank() && it != rejectedSecret }
            ?.also { secret = it }
    }

    private suspend fun rejectSecret(failed: String) {
        val fromWebView = secretMutex.withLock {
            rejectedSecret = failed
            if (secret == failed) secret = null
            failed != manualSecret
        }

        if (!fromWebView) return

        runCatching {
            runWebView(timeout = 10.seconds) {
                onPageFinished {
                    val script = "if(localStorage.getItem('secret')===${failed.toJsonString()}){localStorage.removeItem('secret')}"
                    evaluateJs(script) { resolve(Unit) }
                }
                loadData(baseUrl, "")
            }
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SelectFilter(
            "Genres",
            arrayOf(
                Pair("All", ""),
                Pair("Action", "13"),
                Pair("Adventure", "14"),
                Pair("Comedy", "15"),
                Pair("School Life", "16"),
                Pair("Dark Fantasy", "17"),
                Pair("Suspense", "18"),
                Pair("Historical", "19"),
                Pair("Game", "20"),
                Pair("Media Tie-ins", "21"),
                Pair("LGBTQ+", "253"),
                Pair("Completed", "256"),
                Pair("Own History", HISTORY),
                Pair("Own Favorites", FAVORITES),
            ),
        ),
    )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PAID_PREF
            title = "Hide Locked Chapters"
            summary = "Hide chapters that require points to unlock."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = SECRET_PREF
            title = "Secret Token"
            summary = "Paste your token here to log in with it directly.\n" +
                "Leave empty to log in through WebView."
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_PAID_PREF = "hide_paid_chapters"
        private const val SECRET_PREF = "secret_token"
        private const val FAVORITES = "favorites"
        private const val HISTORY = "history"

        private val EMPTY_BODY = ByteArray(0).toRequestBody()
    }
}
