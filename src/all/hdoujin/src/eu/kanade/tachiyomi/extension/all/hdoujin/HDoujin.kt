package eu.kanade.tachiyomi.extension.all.hdoujin

import CategoryFilter
import SelectFilter
import TagType
import TextFilter
import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.hdoujin.Entries.Entry
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import getFilters
import keiyoushi.utils.getPreferences
import keiyoushi.utils.jsonInstance
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HDoujin(
    override val lang: String,
    private val siteLang: String = lang,
) : HttpSource(), ConfigurableSource {

    override val name = "HDoujin"

    override val supportsLatest = true
    private val preferences = getPreferences()
    private fun quality() = preferences.getString(PREF_IMAGE_RES, "1280")!!
    private fun remadd() = preferences.getBoolean(PREF_REM_ADD, false)
    private fun alwaysIncludeTags() = preferences.getString(PREF_INCLUDE_TAGS, "")
    private fun alwaysExcludeTags() = preferences.getString(PREF_EXCLUDE_TAGS, "")
    private fun getTagsPreference(): String {
        val include = alwaysIncludeTags()
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)

        val exclude = alwaysExcludeTags()
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.map { "-$it" }

        val tags: List<String> = include?.plus(exclude ?: emptyList()) ?: exclude?.plus(include ?: emptyList()) ?: emptyList()
        if (tags.isNotEmpty()) {
            val tagGroups: Map<String, Set<String>> = tags
                .groupBy {
                    val tag = it.removePrefix("-")
                    val parts = tag.split(":", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) parts[0] else "tag"
                }
                .mapValues { (_, values) ->
                    values.mapTo(mutableSetOf()) {
                        val tag = it.removePrefix("-").split(":").last().trim()
                        if (it.startsWith("-")) "-$tag" else tag
                    }
                }

            return tagGroups.entries.joinToString(" ") { (key, values) ->
                "$key:\"${values.joinToString(",")}\""
            }
        }
        return ""
    }

    override val baseUrl: String = "https://hdoujin.org"
    private val baseApiUrl: String = "https://api.hdoujin.org"
    private val bookApiUrl: String = "$baseApiUrl/books"

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var _clearance: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun getClearance(): String? {
        _clearance?.also { return it }
        val latch = CountDownLatch(1)
        handler.post {
            val webview = WebView(context)
            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view!!.evaluateJavascript("window.localStorage.getItem('clearance')") { clearance ->
                        webview.stopLoading()
                        webview.destroy()
                        _clearance = clearance.takeUnless { it == "null" }?.removeSurrounding("\"")
                        latch.countDown()
                    }
                }
            }
            webview.loadDataWithBaseURL("$baseUrl/", " ", "text/html", null, null)
        }
        latch.await(10, TimeUnit.SECONDS)
        return _clearance
    }
    private val clearanceClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val clearance = getClearance()
                ?: throw IOException("Open webview to refresh token")

            val newUrl = url.newBuilder()
                .setQueryParameter("crt", clearance)
                .build()
            val newRequest = request.newBuilder()
                .url(newUrl)
                .build()

            val response = chain.proceed(newRequest)

            if (response.code !in listOf(400, 403)) {
                return@addInterceptor response
            }
            response.close()
            _clearance = null
            throw IOException("Open webview to refresh token")
        }
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(
        bookApiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "8")
            addQueryParameter("page", page.toString())

            val tags = getTagsPreference()
            val terms: MutableList<String> = mutableListOf()
            if (lang != "all") terms += "language:\"^$siteLang\""
            if (tags.isNotBlank()) terms += tags

            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Entries>()

        with(data) {
            return MangasPage(
                mangas = entries.map(Entry::toSManga),
                hasNextPage = limit * page < total,
            )
        }
    }

    override fun latestUpdatesRequest(page: Int) = GET(
        bookApiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            val tags = getTagsPreference()
            val terms: MutableList<String> = mutableListOf()
            if (lang != "all") terms += "language:\"^$siteLang\""
            if (tags.isNotBlank()) terms += tags

            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = bookApiUrl.toHttpUrl().newBuilder().apply {
            val terms = mutableListOf(query.trim())

            if (lang != "all") terms += "language:\"^$siteLang$\""
            filters.forEach { filter ->
                when (filter) {
                    is SelectFilter -> {
                        val value = filter.selected
                        if (value == "popular") {
                            addPathSegment(value)
                        } else {
                            addQueryParameter("sort", value)
                        }
                    }

                    is CategoryFilter -> {
                        val activeFilter = filter.state.filter { it.state }
                        if (activeFilter.isNotEmpty()) {
                            addQueryParameter("cat", activeFilter.sumOf { it.value }.toString())
                        }
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val tags = filter.state.split(",").filter(String::isNotBlank).joinToString(",")
                            if (tags.isNotBlank()) {
                                terms += "${filter.type}:${if (filter.type == "pages") tags else "\"$tags\""}"
                            }
                        }
                    }

                    is TagType -> {
                        if (filter.state > 0) {
                            addQueryParameter(
                                filter.type,
                                when {
                                    filter.type == "i" && filter.state == 0 -> ""
                                    filter.type == "e" && filter.state == 0 -> "1"
                                    else -> ""
                                },
                            )
                        }
                    }
                    else -> {}
                }
            }
            if (query.isNotEmpty()) terms.add("title:\"$query\"")
            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList(): FilterList = getFilters()

    private fun getImagesByMangaData(entry: MangaData, entryId: String, entryKey: String): Pair<ImagesInfo, String> {
        val data = entry.data
        fun getIPK(
            ori: DataKey?,
            alt1: DataKey?,
            alt2: DataKey?,
            alt3: DataKey?,
            alt4: DataKey?,
        ): Pair<Int?, String?> {
            return Pair(
                ori?.id ?: alt1?.id ?: alt2?.id ?: alt3?.id ?: alt4?.id,
                ori?.key ?: alt1?.key ?: alt2?.key ?: alt3?.key ?: alt4?.key,
            )
        }
        val (id, public_key) = when (quality()) {
            "1600" -> getIPK(data.`1600`, data.`1280`, data.`0`, data.`980`, data.`780`)
            "1280" -> getIPK(data.`1280`, data.`1600`, data.`0`, data.`980`, data.`780`)
            "980" -> getIPK(data.`980`, data.`1280`, data.`0`, data.`1600`, data.`780`)
            "780" -> getIPK(data.`780`, data.`980`, data.`0`, data.`1280`, data.`1600`)
            else -> getIPK(data.`0`, data.`1600`, data.`1280`, data.`980`, data.`780`)
        }

        if (id == null || public_key == null) {
            throw Exception("No Images Found")
        }

        val realQuality = when (id) {
            data.`1600`?.id -> "1600"
            data.`1280`?.id -> "1280"
            data.`980`?.id -> "980"
            data.`780`?.id -> "780"
            else -> "0"
        }

        val imagesResponse = clearanceClient.newCall(GET("$bookApiUrl/data/$entryId/$entryKey/$id/$public_key/$realQuality", headers)).execute()
        val images = imagesResponse.parseAs<ImagesInfo>() to realQuality
        return images
    }

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = replace(shortenTitleRegex, "").trim()

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$bookApiUrl/detail/${manga.url}", headers)
    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetail = response.parseAs<MangaDetail>()
        with(mangaDetail) {
            return toSManga().apply {
                setUrlWithoutDomain("${mangaDetail.id}/${mangaDetail.key}")
                title = if (remadd()) {
                    title_short
                        ?: mangaDetail.title.shortenTitle()
                } else {
                    mangaDetail.title
                }
            }
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${manga.url}"
    override fun chapterListRequest(manga: SManga) = GET("$bookApiUrl/detail/${manga.url}", headers)
    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaDetail>()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "${manga.id}/${manga.key}"
                date_upload = (manga.updated_at ?: manga.created_at)
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request =
        POST("$bookApiUrl/detail/${chapter.url}", headers)
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return clearanceClient.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response)
            }
    }
    override fun pageListParse(response: Response): List<Page> {
        val mangaData = response.parseAs<MangaData>()
        val url = response.request.url.toString()
        val matches = Regex("""/detail/(\d+)/([a-z\d]+)""").find(url)
        if (matches == null || matches.groupValues.size < 3) return emptyList()
        val imagesInfo = getImagesByMangaData(mangaData, matches.groupValues[1], matches.groupValues[2])

        return imagesInfo.first.entries.mapIndexed { index, image ->
            Page(index, imageUrl = "${imagesInfo.first.base}/${image.path}?w=${imagesInfo.second}")
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T {
        return jsonInstance.decodeFromString(body.string())
    }

    // Settings
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_RES
            title = "Image Resolution"
            entries = arrayOf("780x", "980x", "1280x", "1600x", "Original")
            entryValues = arrayOf("780", "980", "1280", "1600", "0")
            summary = "%s"
            setDefaultValue("1280")
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_REM_ADD
            title = "Remove additional information in title"
            summary = "Remove anything in brackets from manga titles.\n" +
                "Reload manga to apply changes to loaded manga."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_INCLUDE_TAGS
            title = "Tags to include from browse/search"
            summary = "Separate tags with commas (,).\n" +
                "Excluding: ${alwaysIncludeTags()}"
        }.also(screen::addPreference)
        EditTextPreference(screen.context).apply {
            key = PREF_EXCLUDE_TAGS
            title = "Tags to exclude from browse/search"
            summary = "Separate tags with commas (,). Supports tag types (females, male, etc), defaults to 'tag' if not specified.\n" +
                "Example: 'ai generated, female:hairy, male:hairy'\n" +
                "Excluding: ${alwaysExcludeTags()}"
        }.also(screen::addPreference)
    }
    companion object {
        private const val PREF_REM_ADD = "pref_remove_additional"
        private const val PREF_IMAGE_RES = "pref_image_quality"
        private const val PREF_INCLUDE_TAGS = "pref_include_tags"
        private const val PREF_EXCLUDE_TAGS = "pref_exclude_tags"
    }
}
