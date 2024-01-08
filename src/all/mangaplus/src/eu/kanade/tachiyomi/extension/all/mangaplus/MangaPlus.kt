package eu.kanade.tachiyomi.extension.all.mangaplus

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID

class MangaPlus(
    override val lang: String,
    private val internalLang: String,
    private val langCode: Language,
) : HttpSource(), ConfigurableSource {

    override val name = "MANGA Plus by SHUEISHA"

    override val baseUrl = "https://mangaplus.shueisha.co.jp"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)
        .add("Session-Token", UUID.randomUUID().toString())

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .addInterceptor(::thumbnailIntercept)
        .rateLimitHost(API_URL.toHttpUrl(), 1)
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    private val json: Json by injectLazy()

    private val intl by lazy {
        Intl(
            language = lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "pt-BR", "vi"),
            classLoader = this::class.java.classLoader!!,
        )
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    /**
     * Private cache to find the newest thumbnail URL in case the existing one
     * in Tachiyomi database is expired. It's also used during the chapter deeplink
     * handling to avoid an additional request if possible.
     */
    private var titleCache: Map<Int, Title>? = null

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/hot")
            .set("X-Page", page.toString())
            .build()

        return GET("$API_URL/title_list/ranking?format=json", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            result.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
        }

        val titleList = result.success.titleRankingView!!.titles
            .filter { it.language == langCode }

        titleCache = titleList.associateBy(Title::titleId)

        val page = response.request.headers["X-Page"]!!.toInt()
        val pageList = titleList
            .drop((page - 1) * LISTING_ITEMS_PER_PAGE)
            .take(LISTING_ITEMS_PER_PAGE)
        val hasNextPage = (page + 1) * LISTING_ITEMS_PER_PAGE <= titleList.size

        return MangasPage(pageList.map(Title::toSManga), hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/updates")
            .build()

        return GET("$API_URL/web/web_homeV3?lang=$internalLang&format=json", newHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            result.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
        }

        // Fetch all titles to get newer thumbnail URLs in the interceptor.
        val popularResponse = client.newCall(popularMangaRequest(1)).execute()
            .asMangaPlusResponse()

        if (popularResponse.success != null) {
            titleCache = popularResponse.success.titleRankingView!!.titles
                .filter { it.language == langCode }
                .associateBy(Title::titleId)
        }

        val mangas = result.success.webHomeViewV3!!.groups
            .flatMap(UpdatedTitleV2Group::titleGroups)
            .flatMap(OriginalTitleGroup::titles)
            .map(UpdatedTitle::title)
            .filter { it.language == langCode }
            .map(Title::toSManga)
            .distinctBy(SManga::title)

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.matches(ID_SEARCH_PATTERN)) {
            return mangaDetailsRequest(query.removePrefix(PREFIX_ID_SEARCH))
        } else if (query.matches(CHAPTER_ID_SEARCH_PATTERN)) {
            return pageListRequest(query.removePrefix(PREFIX_CHAPTER_ID_SEARCH))
        }

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/all")
            .set("X-Page", page.toString())
            .build()

        val apiUrl = "$API_URL/title_list/allV2".toHttpUrl().newBuilder()
            .addQueryParameter("filter", query.trim())
            .addQueryParameter("format", "json")

        return GET(apiUrl.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            result.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
        }

        if (result.success.titleDetailView != null) {
            val mangaPlusTitle = result.success.titleDetailView.title
                .takeIf { it.language == langCode }
                ?: return MangasPage(emptyList(), hasNextPage = false)

            return MangasPage(listOf(mangaPlusTitle.toSManga()), hasNextPage = false)
        }

        if (result.success.mangaViewer != null) {
            checkNotNull(result.success.mangaViewer.titleId) { intl["chapter_expired"] }

            val titleId = result.success.mangaViewer.titleId
            val cachedTitle = titleCache?.get(titleId)

            val title = cachedTitle?.toSManga() ?: run {
                val titleRequest = mangaDetailsRequest(titleId.toString())
                val titleResult = client.newCall(titleRequest).execute().asMangaPlusResponse()

                checkNotNull(titleResult.success) {
                    titleResult.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
                }

                titleResult.success.titleDetailView!!
                    .takeIf { it.title.language == langCode }
                    ?.toSManga(intl)
            }

            return MangasPage(listOfNotNull(title), hasNextPage = false)
        }

        val filter = response.request.url.queryParameter("filter").orEmpty()

        val allTitlesList = result.success.allTitlesViewV2!!.allTitlesGroup
            .flatMap(AllTitlesGroup::titles)
            .filter { it.language == langCode }

        titleCache = allTitlesList.associateBy(Title::titleId)

        val searchResults = allTitlesList.filter { title ->
            title.name.contains(filter, ignoreCase = true) ||
                title.author.orEmpty().contains(filter, ignoreCase = true)
        }

        val page = response.request.headers["X-Page"]!!.toInt()
        val pageList = searchResults
            .drop((page - 1) * LISTING_ITEMS_PER_PAGE)
            .take(LISTING_ITEMS_PER_PAGE)
        val hasNextPage = (page + 1) * LISTING_ITEMS_PER_PAGE <= searchResults.size

        return MangasPage(pageList.map(Title::toSManga), hasNextPage)
    }

    // Remove the '#' and map to the new url format used in website.
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.substring(1)

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(mangaUrl: String): Request {
        val titleId = mangaUrl.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/$titleId")
            .build()

        return GET("$API_URL/title_detailV3?title_id=$titleId&format=json", newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            val error = result.error!!.langPopup(langCode)

            when {
                error?.subject == NOT_FOUND_SUBJECT -> intl["title_removed"]
                !error?.body.isNullOrEmpty() -> error!!.body
                else -> intl["unknown_error"]
            }
        }

        val titleDetails = result.success.titleDetailView!!
            .takeIf { it.title.language == langCode }
            ?: throw Exception(intl["not_available"])

        return titleDetails.toSManga(intl)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            val error = result.error!!.langPopup(langCode)

            when {
                error?.subject == NOT_FOUND_SUBJECT -> intl["title_removed"]
                !error?.body.isNullOrEmpty() -> error!!.body
                else -> intl["unknown_error"]
            }
        }

        val titleDetailView = result.success.titleDetailView!!

        return titleDetailView.chapterList
            .filterNot(Chapter::isExpired)
            .map(Chapter::toSChapter)
            .reversed()
    }

    // Remove the '#' and map to the new url format used in website.
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substring(1)

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        return pageListRequest(chapterId)
    }

    private fun pageListRequest(chapterId: String): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/viewer/$chapterId")
            .build()

        val url = "$API_URL/manga_viewer".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", if (preferences.splitImages) "yes" else "no")
            .addQueryParameter("img_quality", preferences.imageQuality)
            .addQueryParameter("format", "json")
            .toString()

        return GET(url, newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            val error = result.error!!.langPopup(langCode)

            when {
                error?.subject == NOT_FOUND_SUBJECT -> intl["chapter_expired"]
                !error?.body.isNullOrEmpty() -> error!!.body
                else -> intl["unknown_error"]
            }
        }

        val referer = response.request.header("Referer")!!

        return result.success.mangaViewer!!.pages
            .mapNotNull(MangaPlusPage::mangaPage)
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "#${page.encryptionKey}"
                Page(i, referer, page.imageUrl + encryptionKey)
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
            key = "${QUALITY_PREF_KEY}_$lang"
            title = intl["image_quality"]
            entries = arrayOf(
                intl["image_quality_low"],
                intl["image_quality_medium"],
                intl["image_quality_high"],
            )
            entryValues = QUALITY_PREF_ENTRY_VALUES
            setDefaultValue(QUALITY_PREF_DEFAULT_VALUE)
            summary = "%s"
        }

        val splitPref = SwitchPreferenceCompat(screen.context).apply {
            key = "${SPLIT_PREF_KEY}_$lang"
            title = intl["split_double_pages"]
            summary = intl["split_double_pages_summary"]
            setDefaultValue(SPLIT_PREF_DEFAULT_VALUE)
        }

        screen.addPreference(qualityPref)
        screen.addPreference(splitPref)
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val encryptionKey = request.url.fragment

        if (encryptionKey.isNullOrEmpty()) {
            return response
        }

        val contentType = response.headers["Content-Type"] ?: "image/jpeg"
        val image = response.body.bytes().decodeXorCipher(encryptionKey)
        val body = image.toResponseBody(contentType.toMediaTypeOrNull())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun thumbnailIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Check if it is 404 to maintain compatibility when the extension used Weserv.
        val isBadCode = (response.code == 401 || response.code == 404)

        if (!isBadCode && !request.url.toString().contains(TITLE_THUMBNAIL_PATH)) {
            return response
        }

        val titleId = request.url.toString()
            .substringBefore("/$TITLE_THUMBNAIL_PATH")
            .substringAfterLast("/")
            .toInt()
        val title = titleCache?.get(titleId) ?: return response

        response.close()
        val thumbnailRequest = GET(title.portraitImageUrl, request.headers)
        return chain.proceed(thumbnailRequest)
    }

    private fun ByteArray.decodeXorCipher(key: String): ByteArray {
        val keyStream = key.chunked(2)
            .map { it.toInt(16) }

        return mapIndexed { i, byte -> byte.toInt() xor keyStream[i % keyStream.size] }
            .map(Int::toByte)
            .toByteArray()
    }

    private fun Response.asMangaPlusResponse(): MangaPlusResponse = use {
        json.decodeFromString(body.string())
    }

    private val SharedPreferences.imageQuality: String
        get() = getString("${QUALITY_PREF_KEY}_$lang", QUALITY_PREF_DEFAULT_VALUE)!!

    private val SharedPreferences.splitImages: Boolean
        get() = getBoolean("${SPLIT_PREF_KEY}_$lang", SPLIT_PREF_DEFAULT_VALUE)

    companion object {
        private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"

        private const val LISTING_ITEMS_PER_PAGE = 20

        private const val QUALITY_PREF_KEY = "imageResolution"
        private val QUALITY_PREF_ENTRY_VALUES = arrayOf("low", "high", "super_high")
        private val QUALITY_PREF_DEFAULT_VALUE = QUALITY_PREF_ENTRY_VALUES[2]

        private const val SPLIT_PREF_KEY = "splitImage"
        private const val SPLIT_PREF_DEFAULT_VALUE = true

        private const val NOT_FOUND_SUBJECT = "Not Found"

        private const val TITLE_THUMBNAIL_PATH = "title_thumbnail_portrait_list"

        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^id:(\\d+)$".toRegex()
        const val PREFIX_CHAPTER_ID_SEARCH = "chapter-id:"
        private val CHAPTER_ID_SEARCH_PATTERN = "^chapter-id:(\\d+)$".toRegex()
    }
}
