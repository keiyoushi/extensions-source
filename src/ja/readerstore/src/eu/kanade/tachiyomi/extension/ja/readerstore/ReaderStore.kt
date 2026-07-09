package eu.kanade.tachiyomi.extension.ja.readerstore

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID.randomUUID

@Source
abstract class ReaderStore :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addNetworkInterceptor(CookieInterceptor(DOMAIN, listOf("safeSearch" to """{"safeAdultGenreFlg":false,"safeNonCherryFlg":false,"safeBLGenreFlg":false,"safeTLGenreFlg":false,"safeBikiniGenreFlg":false}""", "agelimit_auth" to "true")))
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && request.url.encodedPath == "/front-api/viewer/") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 2 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$API_URL/search/detail/".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
            addQueryParameter("cs", "search_keyword")
            addQueryParameter("safeAdult", "false")
            addFilter("sort", filters.firstInstanceOrNull<SortFilter>())
            addFilter("release", filters.firstInstanceOrNull<ReleaseFilter>())
            addFilter("genre", filters.firstInstanceOrNull<GenreFilter>())
            addFilter("sale", filters.firstInstanceOrNull<SaleFilter>())
            addFilter("saleStatus", filters.firstInstanceOrNull<SaleStatusFilter>())
            addFilter("exclude", filters.firstInstanceOrNull<ExcludeFilter>())
            addFilter("priceMin", filters.firstInstanceOrNull<PriceMinFilter>())
            addFilter("priceMax", filters.firstInstanceOrNull<PriceMaxFilter>())
        }.build()
        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        Filter.Header("Note: Novels are not supported!"),
        SortFilter(),
        GenreFilter(),
        ReleaseFilter(),
        SaleFilter(),
        SaleStatusFilter(),
        ExcludeFilter(),
        Filter.Separator(),
        PriceMinFilter(),
        PriceMaxFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>().response
        val mangas = result.docs.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}/"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$API_URL/contents/title/${manga.url}/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", "1")
            .addQueryParameter("count", "1000")
            .addQueryParameter("fields", "detail")
            .addQueryParameter("fields", "title")
            .addQueryParameter("fields", "authors")
            .addQueryParameter("fields", "floor")
            .addQueryParameter("fields", "price")
            .addQueryParameter("fields", "point")
            .addQueryParameter("fields", "browserView")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<List<MangaResponseItem>>().last().toSManga(baseUrl)

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return response.parseAs<List<MangaResponseItem>>()
            .filter { !hideLocked || (!it.isLocked && !it.isPreview) }
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val sample = if (parts.fragment == "1") "true" else "false"
        val chapterId = parts.pathSegments.first()
        val url = "$API_URL/viewer/".toHttpUrl().newBuilder()
            .addQueryParameter("aid", chapterId)
            .addQueryParameter("isSample", sample)
            .addQueryParameter("redirectPathForReadEnd", "")
            .build()
        val response = client.newCall(GET(url, headers)).execute().parseAs<TokenResponse>().token
        val chapterUrl = "$VIEWER_URL/open".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", response.uuid)
            .addQueryParameter("iid", response.browserContentsId)
            .addQueryParameter("auth_token", response.authToken)
            .build()
            .toString()
        return chapterUrl
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val sample = if (parts.fragment == "1") "true" else "false"
        val chapterId = parts.pathSegments.first()
        val url = "$API_URL/viewer/".toHttpUrl().newBuilder()
            .addQueryParameter("aid", chapterId)
            .addQueryParameter("isSample", sample)
            .addQueryParameter("redirectPathForReadEnd", "")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<TokenResponse>().token
        val nmr = randomUUID().toString()
        val newHeaders = headersBuilder()
            .set(HEADER_NMR, nmr)
            .set(HEADER_TOKEN, result.authToken)
            .set(HEADER_USE_CACHE, "false")
            .set(HEADER_UUID, result.uuid)
            .build()

        val base = "$VIEWER_URL/${result.browserContentsId}"
        val metaData = client.newCall(GET("$base/meta".toHttpUrl(), newHeaders)).execute().parseAs<MetaResponse>().data
        val maxIndex = metaData.page.all?.minus(1) ?: throw Exception("Novels are not supported!")
        val cipherKey = extractCipherKey(client.newCall(GET("$base/decrypt", newHeaders)).execute().body.string())

        return (0..maxIndex).map {
            val url = "$base/$PATH_IMAGE_URL".toHttpUrl().newBuilder()
                .addQueryParameter(PARAM_INDICES, it.toString())
                .addQueryParameter(PARAM_CODE, QUALITY_HIGH)
                .addQueryParameter(PARAM_ACCEPT, ACCEPT_FORMATS)
                .fragment("$it;$nmr;${result.authToken};${result.uuid};$maxIndex;$cipherKey;${metaData.type}")
                .build()
            Page(it, imageUrl = url.toString())
        }
    }

    // /decrypt worker: 'var e = [int, int, int, int]'
    private fun extractCipherKey(workerJs: String): String {
        val keyArray = HEADER_KEY.find(workerJs)?.value
            ?: FOUR_INT_ARRAY.findAll(workerJs).map { it.value }.firstOrNull {
                it.trim('[', ']').split(",").map(String::trim) != IV_DIGITS
            }
            ?: throw Exception("missing keys")
        return NUMBER.findAll(keyArray).joinToString("") { "%08x".format(it.value.toLong().toInt()) }
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
        private val HEADER_KEY = Regex("""\be\s*=\s*\[\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*\d+\s*]""")
        private val FOUR_INT_ARRAY = Regex("""\[\s*\d+\s*(?:,\s*\d+\s*){3}]""")
        private val NUMBER = Regex("""\d+""")
        private val IV_DIGITS = listOf("0", "1", "2", "3")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
