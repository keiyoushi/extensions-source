package eu.kanade.tachiyomi.extension.ja.readerstore

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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.UUID.randomUUID

@Source
abstract class ReaderStore :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie(
            listOf(
                "safeSearch" to """{"safeAdultGenreFlg":false,"safeNonCherryFlg":false,"safeBLGenreFlg":false,"safeTLGenreFlg":false,"safeBikiniGenreFlg":false}""",
                "agelimit_auth" to "true",
            ),
        )
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && request.url.encodedPath == "/front-api/viewer/") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 2 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val result = client.get(url).parseAs<SearchResponse>().response
        val mangas = result.docs.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val url = "$API_URL/contents/title/${manga.url}/".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "desc")
            addQueryParameter("page", "1")
            addQueryParameter("count", "1000")
            addQueryParameter("fields", "detail")
            addQueryParameter("fields", "title")
            addQueryParameter("fields", "authors")
            addQueryParameter("fields", "floor")
            addQueryParameter("fields", "price")
            addQueryParameter("fields", "point")
            addQueryParameter("fields", "browserView")
        }.build()

        val items = client.get(url).parseAs<List<MangaResponseItem>>()

        return SMangaUpdate(
            items.last().toSManga(baseUrl),
            items.filter { !hideLocked || (!it.isLocked && !it.isPreview) }
                .map { it.toSChapter() },
        )
    }

    private suspend fun tokenResponse(chapter: SChapter): TokenResponse {
        val url = "$API_URL/viewer/".toHttpUrl().newBuilder()
            .addQueryParameter("aid", chapter.url)
            .addQueryParameter("isSample", chapter.memo["isSample"]!!.boolean.toString())
            .addQueryParameter("redirectPathForReadEnd", "")
            .build()

        return client.get(url).parseAs<TokenResponse>()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val token = runBlocking { tokenResponse(chapter).token }

        return "$VIEWER_URL/open".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", token.uuid)
            .addQueryParameter("iid", token.browserContentsId)
            .addQueryParameter("auth_token", token.authToken)
            .build()
            .toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = coroutineScope {
        val token = tokenResponse(chapter).token
        val nmr = randomUUID().toString()
        val viewerHeaders = headersBuilder()
            .set(HEADER_NMR, nmr)
            .set(HEADER_TOKEN, token.authToken)
            .set(HEADER_USE_CACHE, "false")
            .set(HEADER_UUID, token.uuid)
            .build()

        val base = "$VIEWER_URL/${token.browserContentsId}"
        val metaData = async { client.get("$base/meta", viewerHeaders).parseAs<MetaResponse>().data }
        val cipherKey = async { extractCipherKey(client.get("$base/decrypt", viewerHeaders).use { it.body.string() }) }

        val meta = metaData.await()
        val maxIndex = meta.page.all?.minus(1) ?: throw Exception("Novels are not supported!")
        val key = cipherKey.await()

        (0..maxIndex).map { index ->
            val url = "$base/$PATH_IMAGE_URL".toHttpUrl().newBuilder()
                .addQueryParameter(PARAM_INDICES, index.toString())
                .addQueryParameter(PARAM_CODE, QUALITY_HIGH)
                .addQueryParameter(PARAM_ACCEPT, ACCEPT_FORMATS)
                .fragment("$nmr;${token.authToken};${token.uuid};$maxIndex;$key;${meta.type}")
                .build()
            Page(index, imageUrl = url.toString())
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
}
