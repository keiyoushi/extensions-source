package eu.kanade.tachiyomi.extension.ja.hakusensha

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.clipstudioreader.ClipStudioReaderInterceptor
import keiyoushi.lib.clipstudioreader.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Hakusensha :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "https://product.api.hakusensha-e.net/v1"
    private val preferences by getPreferencesLazy()
    private val authMutex = Mutex()
    private val apiHeaders get() = headersBuilder()
        .set("Authorization", GUEST_TOKEN)
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ClipStudioReaderInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/ranking/week".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "web")
            .addQueryParameter("type", "comic")
            .addQueryParameter("ts", System.currentTimeMillis().toString())
            .build()
        val result = client.get(url, apiHeaders).parseAs<RankingResponse>()
        val mangas = result.ranking.map { it.product.toSManga() }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(ListFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val listFilter = filters.firstInstance<ListFilter>().value
        val url = if (query.isNotBlank()) {
            "$apiUrl/search/keyword".toHttpUrl().newBuilder()
                .addQueryParameter("type", "keyword")
                .addQueryParameter("keyword", query)
                .build()
        } else {
            "$apiUrl/search/$listFilter".toHttpUrl()
        }
        val result = client.get(url, apiHeaders).parseAs<GroupsResponse>()
        val mangas = result.groups.map { it.toSManga() }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val purchased = async {
            val auth = getAuth() ?: return@async emptyList()
            val url = "https://order.api.hakusensha-e.net/v1/users/${auth.userId}/purchasedJdcns".toHttpUrl().newBuilder()
                .addQueryParameter("title_name", manga.url)
                .build()
            val response = client.get(url, userHeaders(auth), ensureSuccess = false)
            if (response.code == 403) {
                response.close()
                expireAuth(auth)
                return@async emptyList()
            }
            response.parseAs<PurchasedResponse>().purchasedList
        }
        val products = async { client.get("$apiUrl/products/group/${manga.url}", apiHeaders).parseAs<ProductsResponse>() }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = products.await()
        val purchasedSet = purchased.await().toSet()
        val now = System.currentTimeMillis()
        val chapterList = result.products
            .filter { (it.releaseAt ?: 0L) <= now && (!hideLocked || !it.isLocked(purchasedSet)) }
            .map { it.toSChapter(purchasedSet) }
            .sortedByDescending { it.chapter_number }

        SMangaUpdate(
            result.toSManga(),
            chapterList,
        )
    }

    override fun getMangaUrl(manga: SManga): String = if (manga.memo["type"]!!.string == "koma") {
        "$baseUrl/store/koma/product/${manga.url}"
    } else {
        "$baseUrl/store/group/${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String = runBlocking { fetchViewerUrl(chapter) }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages(fetchViewerUrl(chapter).toHttpUrl())

    private suspend fun fetchViewerUrl(chapter: SChapter): String {
        val type = chapter.memo["type"]!!.string
        if (type.startsWith("novel")) throw Exception("Novels are not supported.")

        val access = chapter.memo["access"]!!.string
        val auth = if (access == "bookshelf") getAuth() else null
        if (access == "locked" || (access == "bookshelf" && auth == null)) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        val keyPath = if (type == "koma") "koma/$access" else access
        val url = "https://reader.api.hakusensha-e.net/v1/keys/$keyPath/${chapter.url}".toHttpUrl().newBuilder()
            .addQueryParameter("viewer", "bs")
            .addQueryParameter("platform", "web")
            .apply { if (auth != null) addQueryParameter("user_id", auth.userId) }
            .build()

        val newHeaders = if (auth != null) userHeaders(auth) else apiHeaders
        val response = client.get(url, newHeaders, ensureSuccess = false)
        if (auth != null && response.code == 403) {
            response.close()
            expireAuth(auth)
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }
        return response.parseAs<KeyResponse>().bs.url
    }

    private suspend fun getAuth(): UserAuth? = authMutex.withLock {
        val stored = preferences.getString(AUTH_PREF_KEY, null)?.parseAs<UserAuth>()
        if (stored != null && stored.expire > System.currentTimeMillis()) return@withLock stored

        val userId = getLocalStorage(baseUrl, "UserId") ?: return@withLock null
        val token = getLocalStorage(baseUrl, "AuthToken")?.parseAs<AuthToken>()
            ?.takeIf { it.expire > System.currentTimeMillis() && it.token != stored?.token } ?: return@withLock null

        UserAuth(userId, token.token, token.expire).also {
            preferences.edit().putString(AUTH_PREF_KEY, it.toJsonString()).apply()
        }
    }

    private fun expireAuth(auth: UserAuth) {
        preferences.edit().putString(AUTH_PREF_KEY, UserAuth(auth.userId, auth.token, 0L).toJsonString()).apply()
    }

    private fun userHeaders(auth: UserAuth) = headersBuilder()
        .set("Authorization", auth.token)
        .build()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Note: Novels are not supported!"),
        ListFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val AUTH_PREF_KEY = "auth"

        // the site's own fallback guest token, valid until 2030
        private const val GUEST_TOKEN = "a53d742d15704d3ba42ab291994485d0"
    }
}
