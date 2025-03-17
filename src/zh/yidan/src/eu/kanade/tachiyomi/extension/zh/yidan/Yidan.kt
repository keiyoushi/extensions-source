package eu.kanade.tachiyomi.extension.zh.yidan

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.MainThread
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
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Yidan : HttpSource(), ConfigurableSource {
    override val name get() = "一耽女孩"
    override val lang get() = "zh"
    override val supportsLatest get() = true
    private val apiUrl = "https://yd-api.hangtech.cn"
    override val baseUrl: String = getPreferences().run {
        val customBaseUrl = getString(PREF_KEY_CUSTOM_HOST, "")
        if (customBaseUrl.isNullOrEmpty()) {
            val mirrors = MIRRORS
            val index = getPreferences()
                .getString(PREF_KEY_MIRROR, "0")!!.toInt().coerceAtMost(mirrors.size - 1)
            "https://${mirrors[index]}"
        } else {
            customBaseUrl.removeSuffix("/")
        }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val requestUrl = request.url.toString()
        if (requestUrl.contains("images/mhtp/yidan")) {
            // remove first two bytes for image response
            val ext = requestUrl.substringAfterLast(".", "png")
            response.newBuilder().body(
                response.body.source().apply { skip(2) }
                    .asResponseBody("image/$ext".toMediaType()),
            ).build()
        } else {
            response
        }
    }.build()

    private val json: Json by injectLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override fun popularMangaRequest(page: Int) = POST(
        "$baseUrl/api/getByComicByRow",
        headers,
        ComicFetchRequest("29", page, PAGE_SIZE).toJsonRequestBody(),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val records = response.parseAs<CommonResponse<RecordResult>>().result.records
        return createMangasPage(records)
    }

    override fun latestUpdatesRequest(page: Int) = POST(
        "$baseUrl/api/getByComicByRow",
        headers,
        ComicFetchRequest("34", page, PAGE_SIZE).toJsonRequestBody(),
    )

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    private fun searchByKeyword(page: Int, query: String): Request {
        return POST(
            "$apiUrl/api/searchNovel",
            headers,
            KeywordSearchRequest(query).toJsonRequestBody(),
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return searchByKeyword(page, query)
        }
        return POST(
            "$apiUrl/api/getByComicCategoryId",
            headers,
            FilterRequest(
                page = page,
                limit = PAGE_SIZE,
                categoryId = filters.firstInstance<CategoryFilter>().selected,
                orderType = filters.firstInstance<SortFilter>().selected,
                overType = filters.firstInstance<StatusFilter>().selected,
            ).toJsonRequestBody(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchByKeyword = response.request.url.toString().contains("searchNovel")
        val records = when {
            searchByKeyword -> response.parseAs<CommonResponse<List<Record>>>().result
            else -> response.parseAs<CommonResponse<FilterResult>>().result.list
        }
        return createMangasPage(records, paginated = !searchByKeyword)
    }

    private fun createMangasPage(records: List<Record>, paginated: Boolean = true): MangasPage {
        return MangasPage(
            records.map {
                SManga.create().apply {
                    url = "${it.id}"
                    title = it.novelTitle
                    thumbnail_url = it.imgUrl
                }
            },
            paginated && records.size >= PAGE_SIZE,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/pages/comic/info".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .toString()
    }

    override fun mangaDetailsRequest(manga: SManga) = chapterListRequest(manga)

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.parseAs<CommonResponse<ComicInfoResult>>().result.comic
        return SManga.create().apply {
            url = "${comic.id}"
            title = comic.novelTitle
            thumbnail_url = comic.bigImgUrl
            genre = comic.tags
            author = comic.author
            description = comic.introduction
            status = when (comic.overType) {
                1 -> SManga.ONGOING
                2 -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/pages/comic/content".toHttpUrl().newBuilder()
            .addQueryParameter("f", "1")
            .addQueryParameter("s", chapter.chapter_number.toInt().toString())
            .toString()
    }

    override fun chapterListRequest(manga: SManga) = withUserId { userId ->
        POST(
            "$apiUrl/api/getComicInfo",
            headers,
            ComicDetailRequest(manga.url, userId).toJsonRequestBody(),
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = response.parseAs<CommonResponse<ComicInfoResult>>().result.chapterList
        return chapterList.mapIndexed { index, chapter ->
            SChapter.create().apply {
                url = "${chapter.id}"
                name = chapter.chapterName
                date_upload = dateFormat.tryParse(chapter.createTime)
                // used to get the real chapter url
                chapter_number = index.toFloat()
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = withUserId { userId ->
        POST(
            "$apiUrl/api/getComicChapter",
            headers,
            ChapterContentRequest(chapter.url, userId).toJsonRequestBody(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val contentList = response.parseAs<CommonResponse<ChapterContentResult>>().result.content
        return contentList.mapIndexed { index, content ->
            Page(index, imageUrl = content.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        CategoryFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.apply {
            addPreference(
                ListPreference(context).apply {
                    val mirrors = MIRRORS
                    key = PREF_KEY_MIRROR
                    title = "镜像网址（重启生效）"
                    summary = "%s"
                    entries = mirrors
                    entryValues = Array(mirrors.size, Int::toString)
                    setDefaultValue("0")
                },
            )
            addPreference(
                EditTextPreference(context).apply {
                    key = PREF_KEY_CUSTOM_HOST
                    val customUrl = this@Yidan.getPreferences().getString(PREF_KEY_CUSTOM_HOST, "")
                    title = "自定义网址：$customUrl"
                    summary =
                        "请点击后输入自定义网址（例如：https://yidan1.club），如果不需要自定义时请设置为空"
                    setOnPreferenceChangeListener { _, _ ->
                        Toast.makeText(context, "重启应用后生效", Toast.LENGTH_LONG).show()
                        true
                    }
                },
            )
        }
    }

    //region utils functions

    private lateinit var _userId: String

    @MainThread
    private fun WebView.readUserId(block: (userId: String) -> Unit) {
        val script = "javascript:localStorage['uc']"
        evaluateJavascript(script) { uc ->
            if (uc.isNotEmpty() && uc != "null" && uc != "undefined") {
                block(uc.removeSurrounding("'").removeSurrounding("\""))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun <T> withUserId(block: (userId: String) -> T): T {
        return if (this::_userId.isInitialized) {
            block(_userId)
        } else {
            val mainHandler = Handler(Looper.getMainLooper())
            var latch = CountDownLatch(1)
            var webView: WebView? = null
            mainHandler.post {
                webView = WebView(Injekt.get<Application>()).apply {
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        blockNetworkImage = true
                    }
                }
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.readUserId {
                            _userId = it
                            latch.countDown()
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        // wait the auto register request
                        if (request?.url?.encodedPath?.contains("api/regUser") == true) {
                            latch.countDown()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                webView?.loadUrl(baseUrl)
            }
            latch.await(15, TimeUnit.SECONDS)
            if (!this::_userId.isInitialized) {
                latch = CountDownLatch(1)
                mainHandler.postDelayed(
                    {
                        webView?.readUserId {
                            _userId = it
                            latch.countDown()
                        }
                    },
                    500L,
                )
                latch.await(5, TimeUnit.SECONDS)
            }
            mainHandler.post {
                webView?.apply {
                    stopLoading()
                    destroy()
                }
                webView = null
            }
            if (!this::_userId.isInitialized) {
                throw Exception("无法自动获取UserId，请先尝试通过内置WebView进入网站")
            }
            block(_userId)
        }
    }

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody("application/json".toMediaType())
    //endregion

    companion object {
        private const val PREF_KEY_MIRROR = "MIRROR"
        private const val PREF_KEY_CUSTOM_HOST = "CUSTOM_HOST"

        private val MIRRORS = arrayOf("yidan1.club", "yidan22.club", "yidan10.club", "yidan9.club")

        private const val PAGE_SIZE = 16
    }
}
