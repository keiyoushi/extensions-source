package eu.kanade.tachiyomi.extension.zh.jinmantiantangmod

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
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
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.Fetch
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
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
import kotlin.time.Clock
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
            val favoriteSort = filters.firstInstanceOrNull<FavoritesSortFilter>()?.value ?: FAVORITE_SORT_DEFAULT
            return fetchFavorites(page, query, favoriteSort)
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
        if (url.host !in MIRROR_HOSTS) return null
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
    // A logged-in read goes to the login host and skips the app's 10-minute HTTP cache, for a current is_favorite
    private suspend fun album(id: String, member: MemberDto? = null): AlbumDto? {
        val url = if (member == null) apiUrl("album", "id" to id) else loginApiUrl("album", "id" to id)
        val cacheControl = if (member == null) DEFAULT_CACHE_CONTROL else CacheControl.FORCE_NETWORK
        return apiGet<JsonElement>(url, member?.jwttoken, cacheControl)
            .takeIf { it is JsonObject }
            ?.parseAs<AlbumDto>()
    }

    private suspend fun album(manga: SManga): AlbumDto = album(manga.albumId()) ?: throw Exception("作品不存在")

    private fun SManga.albumId() = url.split('/')[2]

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val comments = async { if (fetchDetails) fetchComments(manga.albumId()) else null }
        val loggedIn = preferences.credentials != null
        // is_favorite is only filled for a logged-in request
        val album = if (loggedIn) {
            withLogin { album(manga.albumId(), it) } ?: throw Exception("作品不存在")
        } else {
            album(manga)
        }
        val updatedManga = album.toSManga(coverHost).apply {
            url = manga.url
            // A chapter-only update keeps the stored description, so the comments in it are not dropped
            description = if (fetchDetails) {
                listOfNotNull(description, comments.await()).joinToString("\n\n").ifEmpty { null }
            } else {
                manga.description
            }
        }
        val chapterList = album.toSChapterList()
        if (!loggedIn) return@coroutineScope SMangaUpdate(updatedManga, chapterList)
        // The app has no hook for a source action on a manga, so the site favorite is set by opening this chapter. The url
        // stays the same in both states so a change does not show up as a new chapter; the name says which action it takes.
        val favoriteChapter = SChapter.create().apply {
            url = "$FAVORITE_PATH${manga.albumId()}"
            name = if (album.isFavorite) FAVORITE_REMOVE_NAME else FAVORITE_ADD_NAME
        }
        SMangaUpdate(updatedManga, listOf(favoriteChapter) + chapterList)
    }

    // The latest page of comments; details still load when comments fail
    private suspend fun fetchComments(aid: String): String? = try {
        apiGet<CommentListDto>(apiUrl("forum", "mode" to "all", "aid" to aid, "page" to "1")).toMarkdown()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = album(manga).relatedList.map { it.toSManga(coverHost) }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith(FAVORITE_PATH)) {
            // The reader shows a thrown message as is, which reports the result without a page to render
            throw Exception(favoriteAction(chapter.url.removePrefix(FAVORITE_PATH), add = chapter.name != FAVORITE_REMOVE_NAME))
        }
        val chapterId = chapter.url.split('/')[2]
        // A removed chapter answers an empty array
        val data = apiGet<JsonElement>(apiUrl("comic_read", "id" to chapterId, "express" to ""))
        if (data !is JsonObject) return emptyList()
        return data.parseAs<ChapterImagesDto>().images.mapIndexed { index, image -> Page(index, imageUrl = image.image) }
    }

    // Without a search word and with the default sort the API pages are used as they are. Otherwise every page of the
    // favorites is loaded once, and the search and the sort run in the app
    private suspend fun fetchFavorites(page: Int, query: String, sort: String): MangasPage {
        if (query.isBlank() && sort == FAVORITE_SORT_DEFAULT) {
            val url = favoritesUrl(page, loginHost())
            val favorites = withLogin { apiGet<FavoriteListDto>(url, it.jwttoken, CacheControl.FORCE_NETWORK) }
            return MangasPage(
                favorites.list.map { it.toSManga(coverHost) },
                hasNextPage = page * FAVORITES_PAGE_SIZE < favorites.total.toInt(),
            )
        }
        val matched = allFavorites().filterFavorites(query)
        val sorted = when (sort) {
            "oldest" -> matched.asReversed()
            "title" -> matched.sortedBy { it.title.lowercase() }
            else -> matched
        }
        val from = (page - 1) * FAVORITES_PAGE_SIZE
        return MangasPage(
            sorted.drop(from).take(FAVORITES_PAGE_SIZE),
            hasNextPage = from + FAVORITES_PAGE_SIZE < sorted.size,
        )
    }

    private class FavoritesCache(
        val credentials: Pair<String, String>?,
        val host: String,
        val time: Long,
        val mangas: List<SManga>,
    )

    @Volatile private var favoritesCache: FavoritesCache? = null
    private val favoritesMutex = Mutex()

    // Every page of the favorites, kept for a minute so that paging through a search does not load them again
    private suspend fun allFavorites(): List<SManga> = favoritesMutex.withLock {
        val host = loginHost()
        val credentials = preferences.credentials
        val now = System.currentTimeMillis()
        favoritesCache
            ?.takeIf { it.host == host && it.credentials == credentials && now - it.time < FAVORITES_CACHE_MILLIS }
            ?.let { return@withLock it.mangas }
        val result = mutableListOf<SManga>()
        var page = 1
        while (page <= FAVORITES_MAX_PAGES) {
            val url = favoritesUrl(page, host)
            val favorites = withLogin { apiGet<FavoriteListDto>(url, it.jwttoken, CacheControl.FORCE_NETWORK) }
            result += favorites.list.map { it.toSManga(coverHost) }
            if (favorites.list.isEmpty() || page * FAVORITES_PAGE_SIZE >= favorites.total.toInt()) break
            page++
        }
        val mangas = result.distinctBy { it.url }
        favoritesCache = FavoritesCache(credentials, host, now, mangas)
        mangas
    }

    // Words split by spaces must all be found in the title, author or genre; "-word" leaves out a match
    private fun List<SManga>.filterFavorites(query: String): List<SManga> {
        val terms = query.lowercase().split(' ', '\u3000').filter { it.isNotBlank() }
        val exclude = terms.filter { it.length > 1 && it.startsWith("-") }.map { it.substring(1) }
        val include = terms.filterNot { it.startsWith("-") }.map { it.removePrefix("+") }.filter { it.isNotEmpty() }
        if (include.isEmpty() && exclude.isEmpty()) return this
        return filter { manga ->
            val text = "${manga.title} ${manga.author.orEmpty()} ${manga.genre.orEmpty()}".lowercase()
            include.all { it in text } && exclude.none { it in text }
        }
    }

    // The list lagged behind the site while the album's is_favorite was already current, which points at a cache on
    // the API side keyed by URL: a changing parameter makes every request a new URL
    private fun favoritesUrl(page: Int, host: String): HttpUrl {
        val cacheBuster = System.currentTimeMillis().toString()
        return hostApiUrl(host, "favorite", "page" to "$page", "folder_id" to "0", "o" to "mr", "_" to cacheBuster)
    }

    @Volatile private var pendingFavorite: Pair<String, Long>? = null

    // The reader moves on to the next chapter by itself and downloads open every chapter, so a single open only asks for
    // confirmation; a second open of the same action within the window carries it out
    private suspend fun favoriteAction(aid: String, add: Boolean): String {
        val key = "$aid:$add"
        val now = System.currentTimeMillis()
        val pending = pendingFavorite
        if (pending == null || pending.first != key || now - pending.second > FAVORITE_CONFIRM_MILLIS) {
            pendingFavorite = key to now
            val action = if (add) "加入" else "取消"
            return "要${action}禁漫收藏吗？30 秒内点「重试」或再打开一次本条目确认"
        }
        pendingFavorite = null
        setFavorite(aid, add)
        val result = if (add) "★ 已加入禁漫收藏" else "☆ 已取消禁漫收藏"
        return "$result（下拉刷新章节列表可更新状态）"
    }

    // The app API only toggles. A toggle that went the other way means the album was already in the wanted state
    // (the chapter name was stale), so a second toggle restores it.
    private suspend fun setFavorite(aid: String, add: Boolean) {
        val wanted = if (add) "add" else "remove"
        val first = toggleFavorite(aid)
        favoritesCache = null
        if (first.type == wanted) return
        val second = toggleFavorite(aid)
        favoritesCache = null
        if (second.type != wanted) throw Exception("收藏状态异常：${second.msg}")
    }

    private suspend fun toggleFavorite(aid: String): FavoriteToggleDto = withLogin { member ->
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("aid", aid)
            .build()
        apiPost<FavoriteToggleDto>(loginApiUrl("favorite"), body, member.jwttoken)
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
                add(FavoritesSortFilter())
                add(Filter.Header("收藏夹的搜索和排序在本地进行，首次会加载全部收藏"))
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
        val text = client.get(SERVER_LIST_URL, Fetch.XHR).body.string().trimStart('\uFEFF')
        decrypt(text, SERVER_LIST_SECRET).parseAs<ServerListDto>().server
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FALLBACK_SERVERS
    }

    private suspend fun apiUrl(path: String, vararg params: Pair<String, String>): HttpUrl = hostApiUrl(apiHost(), path, *params)

    // API hosts keep their own copy of the favorite list and some lag behind the site, so logged-in calls can be pinned
    // to a host the login test found current
    private suspend fun loginHost(): String = preferences.favoriteHost ?: apiHost()

    private suspend fun loginApiUrl(path: String, vararg params: Pair<String, String>): HttpUrl = hostApiUrl(loginHost(), path, *params)

    private fun hostApiUrl(host: String, path: String, vararg params: Pair<String, String>): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(host)
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

    private suspend inline fun <reified T> apiGet(
        url: HttpUrl,
        token: String? = null,
        cacheControl: CacheControl = DEFAULT_CACHE_CONTROL,
    ): T {
        val timestamp = timestamp()
        return client.get(url, apiHeaders(timestamp, token), Fetch.XHR, cacheControl).decode(timestamp)
    }

    private suspend inline fun <reified T> apiPost(url: HttpUrl, body: RequestBody, token: String? = null, ensureSuccess: Boolean = true): T {
        val timestamp = timestamp()
        return client.post(url, apiHeaders(timestamp, token), Fetch.XHR, body, ensureSuccess).decode(timestamp, ensureSuccess)
    }

    // The API sometimes answers 200 with a plain-text server error
    private inline fun <reified T> Response.decode(timestamp: String, sessionExpiry: Boolean = true): T {
        val body = try {
            parseAs<ApiResponse>()
        } catch (_: SerializationException) {
            throw Exception("禁漫服务器异常，请稍后重试")
        }
        // A session ended by another login can answer 200 with code 401 in the body, and withLogin retries only on
        // HttpException. The login call itself keeps its errorMsg (wrong password and so on).
        if (body.code == 401 && sessionExpiry) throw HttpException(401)
        if (body.code != 200) throw body.errorMsg?.let(::Exception) ?: HttpException(body.code)
        val data = body.data!!
        val json = if (data is JsonPrimitive && data.isString) decrypt(data.content, "$timestamp$TOKEN_SECRET").parseAs<JsonElement>() else data
        return json.parseAs<T>()
    }

    private fun decrypt(data: String, secret: String): String = Base64.decode(data, Base64.DEFAULT)
        .aesDecrypt(aesEcb(secret.md5().toHexString().encodeToByteArray()))
        .decodeToString()

    private fun timestamp() = Clock.System.now().epochSeconds.toString()

    private val loginMutex = Mutex()

    // The API keeps the login in its AVS cookie
    @Volatile private var session: Session? = null

    // The AVS cookie belongs to one API host, so a login does not carry over when the host is picked again
    private class Session(val credentials: Pair<String, String>, val host: String, val member: MemberDto)

    private suspend fun member(expired: MemberDto? = null): MemberDto {
        val credentials = preferences.credentials ?: throw Exception("请在插件设置中填写用户名和密码")
        return loginMutex.withLock {
            val host = loginHost()
            session?.takeIf { it.credentials == credentials && it.host == host && it.member !== expired }?.member
                ?: login(credentials, host).also { session = Session(credentials, host, it) }
        }
    }

    // A failed login answers 401 with the reason in errorMsg
    private suspend fun login(credentials: Pair<String, String>, host: String): MemberDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("username", credentials.first)
            .addFormDataPart("password", credentials.second)
            .build()
        return apiPost(hostApiUrl(host, "login"), body, ensureSuccess = false)
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
        val daily = apiGet<DailyDto>(loginApiUrl("daily", "user_id" to member.uid), member.jwttoken)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("user_id", member.uid)
            .addFormDataPart("daily_id", daily.dailyId.toString())
            .build()
        apiPost<MessageDto>(loginApiUrl("daily_chk"), body, member.jwttoken).msg
    }

    // Reports what the app API answers for this account, to tell a login problem from a stale favorite list
    private fun runDiagnosis(preference: Preference) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            val result = try {
                diagnose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "失败：${e::class.simpleName} ${e.message}"
            }
            preferences.edit().putString(DIAGNOSIS_PREF, result).apply()
            Handler(Looper.getMainLooper()).post { preference.summary = result }
            showToast(result)
        }
    }

    // Logs in on every API host and reads the favorite count a few times, to find hosts that answer stale lists
    private suspend fun diagnose(): String {
        val credentials = preferences.credentials ?: throw Exception("请先填写用户名和密码")
        // map rather than joinToString's transform, which cannot suspend
        val hosts = fetchServers()
        preferences.edit().putString(KNOWN_HOSTS_PREF, hosts.joinToString(" ")).apply()
        return hosts.map { host ->
            val counts = try {
                val member = login(credentials, host)
                List(DIAGNOSIS_READS) {
                    apiGet<FavoriteListDto>(favoritesUrl(1, host), member.jwttoken, CacheControl.FORCE_NETWORK).total
                }.joinToString(" / ")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "失败 ${e.message}"
            }
            "$host：$counts"
        }.joinToString("\n")
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
            summary = "填写用户名和密码后可使用收藏夹、签到，以及作品章节列表里的收藏切换"
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

        SwitchPreferenceCompat(context).apply {
            title = "测试登录"
            summary = preferences.getString(DIAGNOSIS_PREF, null)
                ?: "在每个接口域名上登录并读取收藏数 3 次，结果会显示在这里"
            setOnPreferenceChangeListener { pref, _ ->
                pref.summary = "测试中…"
                runDiagnosis(pref)
                false
            }
        }.let(screen::addPreference)

        val hosts = preferences.getString(KNOWN_HOSTS_PREF, null)?.split(' ') ?: FALLBACK_SERVERS
        ListPreference(context).apply {
            key = FAVORITE_HOST_PREF
            title = "收藏使用的接口"
            summary = "%s\n收藏夹、收藏状态、收藏切换和签到都走这里。" +
                "选「测试登录」里收藏数和网页一致的域名；列表在测试后更新"
            entries = arrayOf<CharSequence>("自动（随机）") + hosts
            entryValues = arrayOf<CharSequence>("") + hosts
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                session = null
                true
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
        private const val FAVORITES_CACHE_MILLIS = 60_000L
        private const val FAVORITES_MAX_PAGES = 100
        private const val FAVORITE_PATH = "/favorite/"
        private const val FAVORITE_ADD_NAME = "☆【未收藏】点两次加入收藏"
        private const val FAVORITE_REMOVE_NAME = "★【已收藏】点两次取消收藏"
        private const val FAVORITE_CONFIRM_MILLIS = 30_000L
        private const val DIAGNOSIS_READS = 3

        // The mirrors in build.gradle.kts, all deep linked
        private val MIRROR_HOSTS = setOf("18comic.vip", "18comic.ink", "jmcomic-zzz.one", "jmcomic-zzz.org")

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
