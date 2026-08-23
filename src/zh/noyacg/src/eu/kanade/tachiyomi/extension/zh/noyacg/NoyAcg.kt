package eu.kanade.tachiyomi.extension.zh.noyacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.array
import keiyoushi.utils.getObject
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.int
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class NoyAcg :
    KeiSource(),
    ConfigurableSource {

    private val pref by getPreferencesLazy()
    private val imgBaseUrl get() = baseUrl.replace("api", "img")
    private val loginLock = Any()

    @Volatile
    private var sessionGeneration = 0

    override fun getHomeUrl() = WEB_BASE_URL

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context, pref).forEach(screen::addPreference)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "NoyAcg/3.0")
        add("allow-adult", pref.getString(ADULT_PREF, "both")!!)
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        cookieJar(SessionCookieJar(getHomeUrl().toHttpUrl(), network.client.cookieJar))
        addInterceptor { chain ->
            val requestGeneration = sessionGeneration
            val response = chain.proceed(chain.request())
            if (!response.isLoginRequired()) return@addInterceptor response

            response.close()
            synchronized(loginLock) {
                if (requestGeneration == sessionGeneration) {
                    login()
                    sessionGeneration++
                }
            }
            chain.proceed(chain.request()).also {
                if (it.isLoginRequired()) {
                    it.close()
                    throw IOException("登入失敗，請檢查帳號與密碼")
                }
            }
        }
    }

    private fun Response.isLoginRequired(): Boolean = header("Content-Type")?.contains("application/json") == true &&
        runCatching {
            peekBody(LOGIN_RESPONSE_SIZE).string().parseAs<LoginResponseDto>().status == "login"
        }.getOrDefault(false)

    private fun login() {
        val username = pref.getString(USERNAME_PREF, "").orEmpty()
        val password = pref.getString(PASSWORD_PREF, "").orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            throw IOException("請在擴充套件設定中輸入帳號與密碼，或在 WebView 中登入")
        }

        val body = FormBody.Builder()
            .add("user", username)
            .add("pass", password)
            .build()
        val loginHeaders = headers.newBuilder()
            .set("Origin", WEB_BASE_URL)
            .set("Referer", "$WEB_BASE_URL/")
            .build()
        val request = POST("$WEB_BASE_URL/api/login", loginHeaders, body)
        network.client.newCall(request).execute().use { response ->
            val status = response.parseAs<LoginResponseDto>().status
            when (status) {
                "error" -> throw IOException("帳號或密碼錯誤")
                "danger" -> throw IOException("帳號或密碼包含不允許的字元")
                else -> Unit
            }
        }
    }

    private fun Response.parseManga(): MangaDetailDto {
        val jsonObject = parseAs<JsonObject>()
        val status = jsonObject.getStringOrNull("status") ?: "error"
        val book = jsonObject.getObject("book")
        val info = book["info"]!!.parseAs<MangaDto>()
        val recommend = book["recommend"]!!.parseAs<List<RecommendMangaDto>>()
        val categories = jsonObject["chapters"]?.obj?.get("categories")?.array?.map { it.parseAs<CategoryDto>() } ?: emptyList()
        val chaptersMap = mutableMapOf<Int, List<ChapterDto>>()
        jsonObject["chapters"]?.obj?.get("data")?.obj?.forEach { (key, value) ->
            key.toIntOrNull()?.let { categoryId -> chaptersMap[categoryId] = value.array.map { it.parseAs<ChapterDto>() } }
        }
        val chapters = categories.associate { category -> category.name to (chaptersMap[category.id] ?: emptyList()) }
        return MangaDetailDto(status, info, recommend, chapters)
    }

    private fun mangaPageParse(response: Response, page: Int): MangasPage {
        val result = response.parseAs<ListingPageDto>()
        val mangas = result.data.map { it.toSManga(imgBaseUrl) }
        return MangasPage(mangas, page * LISTING_PAGE_SIZE < result.count)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val type = pref.getString(POPULAR_MANGAS_PREF, "day")!!
        val body = FormBody.Builder().addEncoded("type", type).addEncoded("page", page.toString())
        val response = client.post("$baseUrl/api/readLeaderboard", body.build())
        return mangaPageParse(response, page)
    }

    // Updates

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val body = FormBody.Builder().addEncoded("page", page.toString()).addEncoded("sort", "new")
        val response = client.post("$baseUrl/api/b1/booklist", body.build())
        return mangaPageParse(response, page)
    }

    // Search

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = FormBody.Builder().addEncoded("value", query).addEncoded("page", page.toString()).addEncoded("type", "book")
        filters.filterIsInstance<SearchFilter>().forEach { it.addTo(body) }
        val response = client.post("$baseUrl/api/v4/search/fetch", body.build())
        return mangaPageParse(response, page)
    }

    // Manga & Chapters

    override fun getMangaUrl(manga: SManga) = "${getHomeUrl()}/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "${getHomeUrl()}/reader/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = url.pathSegments.last().toIntOrNull()?.let {
        val comic = client.get("$baseUrl/api/v4/book/$it?comment=false").parseManga()
        comic.book.toSManga(imgBaseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl/api/v4/book/${manga.url}?comment=false")
        val comic = response.parseManga()

        val sManga = comic.book.toSManga(imgBaseUrl)

        val mangaId = response.request.url.pathSegments.last()
        val sChapters = if (comic.chapters.isEmpty()) {
            listOf(
                SChapter.create().apply {
                    url = mangaId
                    name = "單章節（${comic.book.len}P）"
                    date_upload = comic.book.time * 1000
                    chapter_number = 0F
                    memo = buildJsonObject { put("size", comic.book.len) }
                },
            )
        } else {
            comic.chapters.flatMap { category ->
                category.value.map {
                    SChapter.create().apply {
                        url = "$mangaId/${it.id}"
                        name = "${it.name}（${it.count}P）"
                        date_upload = it.createdAt * 1000
                        // chapter_number = it.sort.toFloat()
                        scanlator = category.key
                        memo = buildJsonObject { put("size", it.count) }
                    }
                }.reversed()
            }
        }

        return SMangaUpdate(sManga, sChapters)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val comic = client.get("$baseUrl/api/v4/book/${manga.url}?comment=false").parseManga()
        return comic.recommend.map { it.toSManga(imgBaseUrl) }
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter) = List(chapter.memo["size"]!!.int) {
        Page(it, imageUrl = "/${chapter.url}/${it + 1}.webp")
    }

    override fun imageRequest(page: Page) = GET(imgBaseUrl + page.imageUrl, headers)

    companion object {
        const val WEB_BASE_URL = "https://noymanga.com"
        const val LOGIN_RESPONSE_SIZE = 64L
    }
}
