package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.widget.Toast
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesEcb
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Jinmantiantang :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences { preferenceMigration() }

    override val supportsRelatedMangas get() = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ScrambledImageInterceptor)
        addInterceptor(::dropFailedApiHost)
        rateLimit(3, 2.seconds) { it.host == apiHost }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        // The automatic check-in tries once a day
        if (preferences.getBoolean(CHECKIN_PREF, false) && preferences.getString(CHECKIN_DATE_PREF, "") != today()) {
            preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
            checkIn(showError = false)
        }
        return albumList(apiUrl("categories/filter", "page" to "$page", "c" to "0", "o" to "mv"))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = albumList(apiUrl("categories/filter", "page" to "$page", "c" to "0", "o" to "mr"))

    private suspend fun albumList(url: HttpUrl): MangasPage {
        val list = apiGet<AlbumListDto>(url)
        val mangas = list.content.map { it.toSManga(coverHost) }.filterGenre()
        return MangasPage(mangas, list.content.size >= PAGE_SIZE)
    }

    // The site's own image CDN: its cover urls stay the same, unlike the API's rotating image hosts
    private val coverHost get() = "https://cdn-msp.${baseUrl.toHttpUrl().host}"

    private fun List<SManga>.filterGenre(): List<SManga> {
        val removedGenres = preferences.blockList
        if (removedGenres.all { it.isBlank() }) return this
        return filterNot { manga ->
            manga.genre.orEmpty().lowercase().split(", ").any { it in removedGenres }
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (filters.firstInstanceOrNull<FavoritesFilter>()?.isEnabled() == true) {
            return fetchFavorites(page)
        }

        // JM id search: JM123 or 123 opens the album
        val hasIdPrefix = query.startsWith(PREFIX_ID_SEARCH, ignoreCase = true)
        if (hasIdPrefix || (query.isNotEmpty() && query.all(Char::isDigit))) {
            val id = (if (hasIdPrefix) query.substring(PREFIX_ID_SEARCH.length) else query).removePrefix(":").trim()
            return MangasPage(listOfNotNull(fetchAlbum(id)), hasNextPage = false)
        }

        // The API's search: "A +B" is A and B, "A B" is A or B, "-A" leaves out A
        val keywords = listOfNotNull(query.takeIf { it.isNotBlank() }) +
            filters.filterIsInstance<TagFilter>().map { it.value }.filter { it.isNotEmpty() }
        val sort = filters.firstInstance<SortFilter>().value

        if (keywords.isEmpty()) {
            val time = filters.firstInstance<TimeFilter>().value
            val order = if (sort == "mv" && time.isNotEmpty()) "mv_$time" else sort
            val category = filters.firstInstanceOrNull<CategoryFilter>()?.value ?: "0"
            return albumList(apiUrl("categories/filter", "page" to "$page", "c" to category, "o" to order))
        }

        val url = apiUrl(
            "search",
            "search_query" to keywords.joinToString(" +"),
            "o" to sort,
            "search_type" to filters.firstInstance<TypeFilter>().value,
            "page" to "$page",
        )
        val list = apiGet<AlbumListDto>(url)
        list.redirectAid?.let { return MangasPage(listOfNotNull(fetchAlbum(it.toString())), hasNextPage = false) }
        return MangasPage(list.content.map { it.toSManga(coverHost) }.filterGenre(), list.content.size >= PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() !in listOf("album", "photo")) return null
        return fetchAlbum(url.pathSegments.getOrNull(1) ?: return null)
    }

    // A chapter id (from /photo/ links too) answers with its album's id in series_id
    private suspend fun fetchAlbum(id: String): SManga? {
        val album = album(id) ?: return null
        if (album.seriesId != "0" && album.seriesId != id) {
            return album(album.seriesId)?.toSManga(coverHost)
        }
        return album.toSManga(coverHost)
    }

    // Unknown ids answer an empty array
    private suspend fun album(id: String): AlbumDto? = apiGet<JsonElement>(apiUrl("album", "id" to id))
        .takeIf { it is JsonObject }
        ?.parseAs<AlbumDto>()

    private suspend fun album(manga: SManga): AlbumDto = album(manga.albumId()) ?: throw Exception("作品不存在")

    private fun SManga.albumId() = url.split('/')[2]

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val album = album(manga)
        val updatedManga = album.toSManga(coverHost).apply { url = manga.url }
        return SMangaUpdate(updatedManga, album.toSChapterList())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = album(manga).relatedList.map { it.toSManga(coverHost) }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.split('/')[2]
        // A removed chapter answers an empty array
        val data = apiGet<JsonElement>(apiUrl("comic_read", "id" to chapterId, "express" to ""))
        if (data !is JsonObject) return emptyList()
        return data.parseAs<ChapterImagesDto>().images.mapIndexed { index, image -> Page(index, imageUrl = image.image) }
    }

    private suspend fun fetchFavorites(page: Int): MangasPage {
        val url = apiUrl("favorite", "page" to "$page", "folder_id" to "0", "o" to "mr")
        val favorites = withLogin { apiGet<FavoriteListDto>(url, it.jwttoken) }
        return MangasPage(
            favorites.list.map { it.toSManga(coverHost) },
            hasNextPage = page * FAVORITES_PAGE_SIZE < favorites.total.toInt(),
        )
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = apiGet<CategoriesDto>(apiUrl("categories"))
        val categories = data.categories.flatMap { group ->
            val slug = group.slug.ifEmpty { "0" }
            listOf(group.name to slug) + group.subCategories.orEmpty().map { "${group.name} · ${it.name}" to "${slug}_${it.slug}" }
        }
        return FilterData(categories, data.blocks.map { it.title to it.content }).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return FilterList(
            buildList {
                add(FavoritesFilter())
                add(Filter.Separator())
                add(Filter.Header("搜索或选择标签时不按类型和时间筛选"))
                filterData?.let { add(CategoryFilter(it.categories)) }
                filterData?.tagGroups?.forEach { (name, tags) -> add(TagFilter(name, tags)) }
                add(SortFilter())
                add(Filter.Header("时间只用于最多浏览"))
                add(TimeFilter())
                add(TypeFilter())
            },
        )
    }

    private val hostMutex = Mutex()

    @Volatile private var apiHost: String? = null

    private suspend fun apiHost(): String = apiHost ?: hostMutex.withLock {
        apiHost ?: fetchServers().random().also { apiHost = it }
    }

    // An API host that stops answering is picked again from the list on the next request
    private fun dropFailedApiHost(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: IOException) {
        if (!chain.call().isCanceled() && chain.request().url.host == apiHost) apiHost = null
        throw e
    }

    // The app's server list, or a snapshot of it when the list fails
    private suspend fun fetchServers(): List<String> = try {
        val text = client.get(SERVER_LIST_URL).body.string().trimStart('\uFEFF')
        decrypt(text, SERVER_LIST_SECRET).parseAs<ServerListDto>().server
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FALLBACK_SERVERS
    }

    private suspend fun apiUrl(path: String, vararg params: Pair<String, String>): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(apiHost())
        .addPathSegments(path)
        .apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            addQueryParameter("lang", "CN")
        }
        .build()

    private fun apiHeaders(timestamp: String, token: String?): Headers = headers.newBuilder()
        .set("Token", "$timestamp$TOKEN_SECRET".md5().toHexString())
        .set("Tokenparam", "$timestamp,$APP_VERSION")
        .apply { if (token != null) set("Authorization", "Bearer $token") }
        .build()

    private suspend inline fun <reified T> apiGet(url: HttpUrl, token: String? = null): T {
        val timestamp = timestamp()
        return client.get(url, apiHeaders(timestamp, token)).decode(timestamp)
    }

    private suspend inline fun <reified T> apiPost(url: HttpUrl, body: RequestBody, token: String? = null, ensureSuccess: Boolean = true): T {
        val timestamp = timestamp()
        return client.post(url, apiHeaders(timestamp, token), body, ensureSuccess).decode(timestamp)
    }

    // The API sometimes answers 200 with a plain-text server error
    private inline fun <reified T> Response.decode(timestamp: String): T {
        val body = try {
            parseAs<ApiResponse>()
        } catch (_: SerializationException) {
            throw Exception("禁漫服务器异常，请稍后重试")
        }
        if (body.code != 200) throw body.errorMsg?.let(::Exception) ?: HttpException(body.code)
        val data = body.data!!
        val json = if (data is JsonPrimitive && data.isString) decrypt(data.content, "$timestamp$TOKEN_SECRET").parseAs<JsonElement>() else data
        return json.parseAs<T>()
    }

    private fun decrypt(data: String, secret: String): String = Base64.decode(data, Base64.DEFAULT)
        .aesDecrypt(aesEcb(secret.md5().toHexString().encodeToByteArray()))
        .decodeToString()

    private fun timestamp() = (System.currentTimeMillis() / 1000).toString()

    private val loginMutex = Mutex()

    // The API keeps the login in its AVS cookie
    @Volatile private var session: Session? = null

    private class Session(val credentials: Pair<String, String>, val member: MemberDto)

    private suspend fun member(expired: MemberDto? = null): MemberDto {
        val credentials = preferences.credentials ?: throw Exception("请在插件设置中填写用户名和密码")
        return loginMutex.withLock {
            session?.takeIf { it.credentials == credentials && it.member !== expired }?.member
                ?: login(credentials).also { session = Session(credentials, it) }
        }
    }

    // A failed login answers 401 with the reason in errorMsg
    private suspend fun login(credentials: Pair<String, String>): MemberDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("username", credentials.first)
            .addFormDataPart("password", credentials.second)
            .build()
        return apiPost(apiUrl("login"), body, ensureSuccess = false)
    }

    // A session the server has ended answers 401: log in again once
    private suspend inline fun <reified T> withLogin(call: (MemberDto) -> T): T {
        val member = member()
        return try {
            call(member)
        } catch (e: HttpException) {
            if (e.code != 401) throw e
            call(member(expired = member))
        }
    }

    private fun checkIn(showError: Boolean) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val msg = performCheckIn()
                preferences.edit().putString(CHECKIN_DATE_PREF, today()).apply()
                showToast("签到：$msg")
            } catch (e: Exception) {
                if (showError) showToast("签到失败：${e.message}")
            }
        }
    }

    private suspend fun performCheckIn(): String = withLogin { member ->
        val daily = apiGet<DailyDto>(apiUrl("daily", "user_id" to member.uid), member.jwttoken)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("user_id", member.uid)
            .addFormDataPart("daily_id", daily.dailyId.toString())
            .build()
        apiPost<MessageDto>(apiUrl("daily_chk"), body, member.jwttoken).msg
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun today(): String = LocalDate.now().toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = USERNAME_PREF
            title = "用户名"
            summary = "填写用户名和密码后可使用收藏夹和签到"
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(context).apply {
            key = PASSWORD_PREF
            title = "密码"
            summary = if (preferences.getString(key, "").isNullOrEmpty()) "未设置" else "已设置"
            dialogTitle = title
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { pref, newValue ->
                (pref as EditTextPreference).summary = if ((newValue as String).isEmpty()) "未设置" else "已设置"
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(context).apply {
            key = CHECKIN_PREF
            title = "自动签到"
            summary = "每天第一次打开热门时自动签到"
            setDefaultValue(false)
        }.let(screen::addPreference)

        // A switch that never turns on: the extensions lib has no plain clickable Preference
        SwitchPreferenceCompat(context).apply {
            title = "立即签到"
            summary = "点击立即签到一次"
            setOnPreferenceChangeListener { _, _ ->
                checkIn(showError = true)
                false
            }
        }.let(screen::addPreference)

        EditTextPreference(context).apply {
            key = BLOCK_PREF
            title = "屏蔽词列表"
            setDefaultValue(
                "// 例如 \"YAOI cos 扶他 毛絨絨 獵奇 韩漫 韓漫\", " +
                    "关键词之间用空格分离, 大小写不敏感, \"//\"后的字符会被忽略",
            )
            dialogTitle = "关键词列表"
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREFIX_ID_SEARCH = "JM"
        private const val PAGE_SIZE = 80
        private const val FAVORITES_PAGE_SIZE = 20

        private const val APP_VERSION = "2.1.12"
        private const val TOKEN_SECRET = "185Hcomic3PAPP7R"
        private const val SERVER_LIST_URL = "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt"
        private const val SERVER_LIST_SECRET = "diosfjckwpqpdfjkvnqQjsik"

        // The server list on 2026-10-28
        private val FALLBACK_SERVERS = listOf(
            "www.cdnhjk.net",
            "www.cdnutc.cc",
            "www.cdngwc.net",
            "www.cdngwc.club",
        )
    }
}
