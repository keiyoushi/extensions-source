package eu.kanade.tachiyomi.extension.all.mangaplus

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)
        .add("SESSION-TOKEN", UUID.randomUUID().toString())

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
    private val titleCache = mutableMapOf<Int, Title>()
    private lateinit var directory: List<Title>

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int) =
        GET("$API_URL/title_list/rankingV2?lang=$internalLang&type=hottest&clang=$internalLang&format=json", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            result.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
        }

        directory = result.success.titleRankingViewV2!!.rankedTitles
            .flatMap(RankedTitle::titles)
            .filter { it.language == langCode }
        titleCache.putAll(directory.associateBy(Title::titleId))

        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val pageList = directory
            .drop((page - 1) * LISTING_ITEMS_PER_PAGE)
            .take(LISTING_ITEMS_PER_PAGE)
        val hasNextPage = (page + 1) * LISTING_ITEMS_PER_PAGE <= directory.size

        return MangasPage(pageList.map(Title::toSManga), hasNextPage)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { latestUpdatesParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$API_URL/web/web_homeV4?lang=$internalLang&clang=$internalLang&format=json", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asMangaPlusResponse()

        checkNotNull(result.success) {
            result.error!!.langPopup(langCode)?.body ?: intl["unknown_error"]
        }

        directory = result.success.webHomeViewV4!!.groups
            .flatMap(UpdatedTitleV2Group::titleGroups)
            .flatMap(OriginalTitleGroup::titles)
            .map(UpdatedTitle::title)
            .filter { it.language == langCode }
            .distinctBy(Title::titleId)

        titleCache.putAll(directory.associateBy(Title::titleId))
        titleCache.putAll(
            result.success.webHomeViewV4.rankedTitles
                .flatMap(RankedTitle::titles)
                .filter { it.language == langCode }
                .associateBy(Title::titleId),
        )
        titleCache.putAll(
            result.success.webHomeViewV4.featuredTitleLists
                .flatMap(FeaturedTitleList::featuredTitles)
                .filter { it.language == langCode }
                .associateBy(Title::titleId),
        )

        return parseDirectory(1)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, query) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.matches(ID_SEARCH_PATTERN)) {
            return mangaDetailsRequest(query.removePrefix(PREFIX_ID_SEARCH))
        } else if (query.matches(CHAPTER_ID_SEARCH_PATTERN)) {
            return pageListRequest(query.removePrefix(PREFIX_CHAPTER_ID_SEARCH))
        }

        return GET("$API_URL/title_list/allV2?format=json", headers)
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, query: String): MangasPage {
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
            val cachedTitle = titleCache[titleId]

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

        val allTitlesList = result.success.allTitlesViewV2!!.allTitlesGroup
            .flatMap(AllTitlesGroup::titles)
            .filter { it.language == langCode }

        titleCache.putAll(allTitlesList.associateBy(Title::titleId))
        directory = allTitlesList.filter { title ->
            title.name.contains(query, ignoreCase = true) ||
                title.author.orEmpty().contains(query, ignoreCase = true)
        }

        return parseDirectory(1)
    }

    // Remove the '#' and map to the new url format used in website.
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.substring(1)

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(mangaUrl: String): Request {
        val titleId = mangaUrl.substringAfterLast("/")

        return GET("$API_URL/title_detailV3?title_id=$titleId&format=json", headers)
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
        val subtitleOnly = preferences.subtitleOnly()

        return titleDetailView.chapterList
            .filterNot(Chapter::isExpired)
            .map { it.toSChapter(subtitleOnly) }
            .reversed()
    }

    // Remove the '#' and map to the new url format used in website.
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substring(1)

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        return pageListRequest(chapterId)
    }

    private fun pageListRequest(chapterId: String): Request {
        val url = "$API_URL/manga_viewer".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", if (preferences.splitImages()) "yes" else "no")
            .addQueryParameter("img_quality", preferences.imageQuality())
            .addQueryParameter("format", "json")
            .toString()

        return GET(url, headers)
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

        return result.success.mangaViewer!!.pages
            .mapNotNull(MangaPlusPage::mangaPage)
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "#${page.encryptionKey}"
                Page(i, imageUrl = page.imageUrl + encryptionKey)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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

        val titlePref = SwitchPreferenceCompat(screen.context).apply {
            key = "${SUBTITLE_ONLY_KEY}_$lang"
            title = intl["subtitle_only"]
            summary = intl["subtitle_only_summary"]
            setDefaultValue(SUBTITLE_ONLY_DEFAULT_VALUE)
        }

        screen.addPreference(qualityPref)
        screen.addPreference(splitPref)
        screen.addPreference(titlePref)
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
        val title = titleCache[titleId] ?: return response

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

    private fun SharedPreferences.imageQuality(): String = getString("${QUALITY_PREF_KEY}_$lang", QUALITY_PREF_DEFAULT_VALUE)!!

    private fun SharedPreferences.splitImages(): Boolean = getBoolean("${SPLIT_PREF_KEY}_$lang", SPLIT_PREF_DEFAULT_VALUE)

    private fun SharedPreferences.subtitleOnly(): Boolean = getBoolean("${SUBTITLE_ONLY_KEY}_$lang", SUBTITLE_ONLY_DEFAULT_VALUE)

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_CHAPTER_ID_SEARCH = "chapter-id:"
    }
}

private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"

private const val LISTING_ITEMS_PER_PAGE = 20

private const val QUALITY_PREF_KEY = "imageResolution"
private val QUALITY_PREF_ENTRY_VALUES = arrayOf("low", "high", "super_high")
private val QUALITY_PREF_DEFAULT_VALUE = QUALITY_PREF_ENTRY_VALUES[2]

private const val SPLIT_PREF_KEY = "splitImage"
private const val SPLIT_PREF_DEFAULT_VALUE = true

private const val SUBTITLE_ONLY_KEY = "subtitleOnly"
private const val SUBTITLE_ONLY_DEFAULT_VALUE = false

private const val NOT_FOUND_SUBJECT = "Not Found"

private const val TITLE_THUMBNAIL_PATH = "title_thumbnail_portrait_list"

private val ID_SEARCH_PATTERN = "^id:(\\d+)$".toRegex()
private val CHAPTER_ID_SEARCH_PATTERN = "^chapter-id:(\\d+)$".toRegex()
