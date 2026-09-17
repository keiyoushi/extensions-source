package eu.kanade.tachiyomi.extension.zh.picacomic

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Picacomic :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val apiUrl = "https://picaapi.picacomic.com"

    override fun OkHttpClient.Builder.configureClient() = apply {
        dns(ChannelDns(apiUrl.substringAfter("."), network.client, preferences))
        addInterceptor { chain ->
            var request = chain.request()

            if (request.url.encodedPath.endsWith("/auth/sign-in")) {
                return@addInterceptor chain.proceed(request)
            }

            if (token.isEmpty()) getToken()

            request = request.applyToken()

            var response = chain.proceed(request)

            if (response.code == 401) {
                response.close()
                getToken()
                response = chain.proceed(request.applyToken())
            }

            response
        }
    }

    private val basicHeaders get() = mapOf(
        "api-key" to "C69BAF41DA5ABD1FFEDC6D2FEA56B",
        "app-channel" to channel,
        "app-version" to "2.2.1.3.3.4",
        "app-uuid" to "defaultUuid",
        "app-platform" to "android",
        "app-build-version" to "44",
        "User-Agent" to "okhttp/3.8.1",
        "accept" to "application/vnd.picacomic.com.v1+json",
        "image-quality" to quality,
        "Content-Type" to "application/json; charset=UTF-8", // must be exactly matched!
    )

    // Latest + Popular
    override suspend fun getPopularManga(page: Int) = parseSearchManga(client.get("$apiUrl/comics?page=$page&s=dd"))

    override suspend fun getLatestUpdates(page: Int) = singlePageParse(client.get("$apiUrl/comics/random"), true)

    private fun singlePageParse(response: Response, isRandom: Boolean = false): MangasPage {
        val comics = response.parseAs<PicaResponse>().data.comics!!.parseAs<List<PicaSearchComic>>()

        val mangas = comics
            .filter { !hitBlocklist(it) }
            .map { comic ->
                SManga.create().apply {
                    title = comic.title
                    author = comic.author
                    thumbnail_url = comic.thumb.let {
                        it.fileServer + "/static/" + it.path
                    }
                    url = "$apiUrl/comics/${comic._id}"
                    status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
                }
            }

        return MangasPage(mangas, isRandom)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var sort: String? = null
        var category: String? = null
        var rankPath: String? = null
        var onlyFavourite: Boolean? = false

        // parse filters
        for (filter in filters) {
            when (filter) {
                is SortFilter -> sort = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is RankFilter -> rankPath = filter.toUriPart()
                is FavouriteFilter -> onlyFavourite = filter.state
                else -> {}
            }
        }

        // return user's favourite list if check
        if (onlyFavourite == true) {
            val url = "$apiUrl/users/favourite".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("s", sort ?: "dd")
                .build()

            return parseSearchManga(client.get(url))
        }

        // return comics from leaderboard
        if (!rankPath.isNullOrEmpty()) {
            return singlePageParse(client.get("$apiUrl$rankPath"))
        }

        if (query.isEmpty()) {
            val url = "$apiUrl/comics".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("s", sort ?: "dd")
                .apply {
                    category?.takeIf(String::isNotEmpty)?.let { addQueryParameter("c", it) }
                }
                .build()

            return parseSearchManga(client.get(url))
        }

        // return comics from some search
        val url = "$apiUrl/comics/advanced-search?page=$page"

        val body = PicaSearchPayload(query, sort ?: "dd").toJsonString().toRequestBody()

        return parseSearchManga(client.post(url, body))
    }

    private fun parseSearchManga(response: Response): MangasPage {
        if (response.request.url.toString().contains("/comics/leaderboard".toRegex())) {
            return singlePageParse(response)
        }

        val comics = response.parseAs<PicaResponse>().data.comics!!.parseAs<PicaSearchComics>()

        val mangas = comics.docs
            .filter { !hitBlocklist(it) }
            .map { comic ->
                SManga.create().apply {
                    title = comic.title
                    author = comic.author
                    thumbnail_url = comic.thumb.let { "${it.fileServer}/static/${it.path}" }
                    url = "$apiUrl/comics/${comic._id}"
                    status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
                }
            }

        return MangasPage(mangas, comics.page < comics.pages)
    }

    // Details + Chapters
    override fun getMangaUrl(manga: SManga) = "$baseUrl/comic/${manga.url.comicId()}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comic/reader/${chapter.url.comicId()}/" + chapter.url.substringAfterLast("/")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val comicId = url.pathSegments.getOrNull(1) ?: return null
        return getMangaDetails(
            SManga.create().apply { this.url = "$apiUrl/comics/$comicId" },
        )
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get(manga.url)
        val comic = response.parseAs<PicaResponse>().data.comic!!

        return SManga.create().apply {
            url = manga.url
            title = comic.title
            author = comic.author
            description = comic.description
            artist = comic.artist
            genre = ((comic.tags ?: (emptyList<String>() + comic.categories)))
                .map(String::trim)
                .distinct()
                .joinToString(", ")
            status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = comic.thumb.let { "${it.fileServer}/static/${it.path}" }
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val response = client.get("${manga.url}/eps?page=1")
        val eps = response.parseAs<PicaResponse>().data.eps!!

        val comicId = manga.url.comicId()

        return coroutineScope {
            val restPages = (2..eps.pages).map { page ->
                async {
                    client.get("${manga.url}/eps?page=$page").parseAs<PicaResponse>().data.eps!!.docs
                }
            }
            listOf(eps.docs) + restPages.awaitAll()
        }
            .flatMap { docs ->
                docs.map {
                    SChapter.create().apply {
                        name = it.title
                        chapter_number = it.order.toFloat()
                        url = "$apiUrl/comics/$comicId/order/${it.order}"
                        date_upload = Instant.tryParse(it.updated_at)
                    }
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(chapter.url + "/pages?page=1")
        val firstPage = response.parseAs<PicaResponse>().data.pages!!

        return coroutineScope {
            val restPages = (2..firstPage.pages).map { page ->
                async {
                    val nextUrl = chapter.url + "/pages?page=$page"
                    client.get(nextUrl).parseAs<PicaResponse>().data.pages!!
                }
            }
            listOf(firstPage) + restPages.awaitAll()
        }
            .flatMap { pages ->
                pages.docs.mapIndexed { index, picaPage ->
                    val url = picaPage.media.let { "${it.fileServer}/static/${it.path}" }
                    Page(index + (pages.page - 1) * pages.limit, "", url)
                }
            }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        CategoryFilter(),
        RankFilter(),
        FavouriteFilter(),
    )

    // Preferences
    private var token = preferences.getString("TOKEN", "")!!
        set(value) {
            field = value
            preferences.edit().putString("TOKEN", value).apply()
        }

    private val blocklist get() = preferences.getString("BLOCK_GENRES", "")!!
        .split(',').map { it.trim() }
    private val username get() = preferences.getString("USERNAME", "")!!
    private val password get() = preferences.getString("PASSWORD", "")!!
    private val quality get() = preferences.getString("IMAGE_QUALITY", "original")!!
    private val channel get() = preferences.getString(APP_CHANNEL, "2")!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "USERNAME"
            title = "用户名"
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "PASSWORD"
            title = "密码"
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "BLOCK_GENRES"
            title = "屏蔽词列表"
            dialogTitle = "屏蔽词列表"
            dialogMessage = "根据关键词过滤漫画，关键词之间用','分离。" +
                "关键词分为分类和标签两种，在热门和最新中只能按分类过滤（即在filter的类型中出现的词），" +
                "而在搜索中两者都可以"
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "IMAGE_QUALITY"
            title = "图片质量"
            entries = arrayOf("原图", "低", "中", "高")
            entryValues = arrayOf("original", "low", "medium", "high")
            setDefaultValue("original")
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = APP_CHANNEL
            title = "分流"
            entries = arrayOf("1", "2", "3")
            entryValues = entries
            setDefaultValue("1")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = APP_CHANNEL_URL
            title = "分流url"
            summary =
                "自定义用于获取分流2、3的目标地址；分流1不受影响；（如果之前获取成功了需要重启才能生效，如果出现超时可以多重试几次）"
        }.let(screen::addPreference)
    }

    // Utils
    private fun hitBlocklist(comic: PicaSearchComic) = ((comic.tags ?: (emptyList<String>() + comic.categories)))
        .map(String::trim)
        .any { it in blocklist }

    private fun encrypt(url: String, time: Long, method: String, nonce: String): String {
        val hmacSha256Key = "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"
        val apiKey = basicHeaders["api-key"]
        val path = url.substringAfter("$apiUrl/")
        val raw = "$path$time$nonce${method}$apiKey".lowercase(Locale.ROOT)
        return hmacSHA256(hmacSha256Key, raw).convertToString()
    }

    private fun getToken() {
        if (username.isEmpty() || password.isEmpty()) throw IOException("请在扩展设置界面输入用户名和密码")
        token = fetchToken(username, password)
    }

    private fun fetchToken(username: String, password: String): String {
        val url = "$apiUrl/auth/sign-in"
        val body = PicaLoginPayload(username, password).toJsonString().toRequestBody()

        val response = client.newCall(
            POST(url, picaHeaders(url, "POST"), body),
        ).execute()
        if (!response.isSuccessful) throw IOException("登录失败")
        return response.parseAs<PicaResponse>().data.token!!
    }

    private fun picaHeaders(url: String, method: String = "GET"): Headers {
        val time = System.currentTimeMillis() / 1000
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val nonce = (1..32).map { allowedChars.random() }
            .joinToString("")
        val signature = encrypt(url, time, method, nonce)
        return basicHeaders.toMutableMap().apply {
            put("time", time.toString())
            put("nonce", nonce)
            put("signature", signature)
            put("authorization", token)
        }.toHeaders()
    }

    private suspend fun OkHttpClient.get(url: String) = get(url, picaHeaders(url))
    private suspend fun OkHttpClient.get(url: HttpUrl) = get(url, picaHeaders(url.toString()))
    private suspend fun OkHttpClient.post(url: String, body: RequestBody) = post(url, picaHeaders(url, "POST"), body)

    private fun Request.applyToken() = newBuilder().header("Authorization", token).build()

    private fun String.comicId() = toHttpUrl().pathSegments[1]
}

const val APP_CHANNEL = "APP_CHANNEL"
const val APP_CHANNEL_URL = "APP_CHANNEL_URL"
