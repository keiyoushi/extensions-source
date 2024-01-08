package eu.kanade.tachiyomi.multisrc.bilibili

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Bilibili(
    override val name: String,
    final override val baseUrl: String,
    final override val lang: String,
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::expiredImageTokenIntercept)
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .rateLimitHost(CDN_URL.toHttpUrl(), 2)
        .rateLimitHost(COVER_CDN_URL.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT_JSON)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    protected open val intl by lazy { BilibiliIntl(lang) }

    private val apiLang: String = when (lang) {
        BilibiliIntl.SIMPLIFIED_CHINESE -> "cn"
        else -> lang
    }

    protected open val defaultPopularSort: Int = 0

    protected open val defaultLatestSort: Int = 1

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    protected val json: Json by injectLazy()

    protected open val signedIn: Boolean = false

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page = page,
        query = "",
        filters = FilterList(
            SortFilter("", getAllSortOptions(), defaultPopularSort),
        ),
    )

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page = page,
        query = "",
        filters = FilterList(
            SortFilter("", getAllSortOptions(), defaultLatestSort),
        ),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        ID_SEARCH_PATTERN.matchEntire(query)?.let {
            val (id) = it.destructured
            val temporaryManga = SManga.create().apply { url = "/detail/mc$id" }
            return mangaDetailsRequest(temporaryManga)
        }

        val price = filters.firstInstanceOrNull<PriceFilter>()?.state ?: 0

        val jsonPayload = buildJsonObject {
            put("area_id", filters.firstInstanceOrNull<AreaFilter>()?.selected?.id ?: -1)
            put("is_finish", filters.firstInstanceOrNull<StatusFilter>()?.state?.minus(1) ?: -1)
            put("is_free", if (price == 0) -1 else price)
            put("order", filters.firstInstanceOrNull<SortFilter>()?.selected?.id ?: 0)
            put("page_num", page)
            put("page_size", if (query.isBlank()) POPULAR_PER_PAGE else SEARCH_PER_PAGE)
            put("style_id", filters.firstInstanceOrNull<GenreFilter>()?.selected?.id ?: -1)
            put("style_prefer", "[]")

            if (query.isNotBlank()) {
                put("need_shield_prefer", true)
                put("key_word", query)
            }
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val refererUrl = if (query.isBlank()) {
            "$baseUrl/genre"
        } else {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .toString()
        }

        val newHeaders = headersBuilder()
            .set("Referer", refererUrl)
            .build()

        val apiUrl = "$baseUrl/$API_COMIC_V1_COMIC_ENDPOINT/".toHttpUrl().newBuilder()
            .addPathSegment(if (query.isBlank()) "ClassPage" else "Search")
            .addCommonParameters()
            .toString()

        return POST(apiUrl, newHeaders, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        if (requestUrl.contains("ComicDetail")) {
            val comic = mangaDetailsParse(response)
            return MangasPage(listOf(comic), hasNextPage = false)
        }

        if (requestUrl.contains("ClassPage")) {
            val result = response.parseAs<List<BilibiliComicDto>>()

            if (result.code != 0) {
                return MangasPage(emptyList(), hasNextPage = false)
            }

            val comicList = result.data!!.map(::searchMangaFromObject)
            val hasNextPage = comicList.size == POPULAR_PER_PAGE

            return MangasPage(comicList, hasNextPage)
        }

        val result = response.parseAs<BilibiliSearchDto>()

        if (result.code != 0) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val comicList = result.data!!.list.map(::searchMangaFromObject)
        val hasNextPage = comicList.size == SEARCH_PER_PAGE

        return MangasPage(comicList, hasNextPage)
    }

    private fun searchMangaFromObject(comic: BilibiliComicDto): SManga = SManga.create().apply {
        title = Jsoup.parse(comic.title).text()
        thumbnail_url = comic.verticalCover + THUMBNAIL_RESOLUTION

        val comicId = if (comic.id == 0) comic.seasonId else comic.id
        url = "/detail/mc$comicId"
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val comicId = manga.url.substringAfterLast("/mc").toInt()

        val jsonPayload = buildJsonObject { put("comic_id", comicId) }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + manga.url)
            .build()

        val apiUrl = "$baseUrl/$API_COMIC_V1_COMIC_ENDPOINT/ComicDetail".toHttpUrl()
            .newBuilder()
            .addCommonParameters()
            .toString()

        return POST(apiUrl, newHeaders, requestBody)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val comic = response.parseAs<BilibiliComicDto>().data!!

        title = comic.title
        author = comic.authorName.joinToString()
        genre = comic.styles.joinToString()
        status = when {
            comic.isFinish == 1 -> SManga.COMPLETED
            comic.isOnHiatus -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
        description = buildString {
            if (comic.hasPaidChapters && !signedIn) {
                append("${intl.hasPaidChaptersWarning(comic.paidChaptersCount)}\n\n")
            }

            append(comic.classicLines)

            if (comic.updateWeekdays.isNotEmpty() && status == SManga.ONGOING) {
                append("\n\n${intl.informationTitle}:")
                append("\n• ${intl.getUpdateDays(comic.updateWeekdays)}")
            }
        }
        thumbnail_url = comic.verticalCover
        url = "/detail/mc" + comic.id
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<BilibiliComicDto>()

        if (result.code != 0) {
            return emptyList()
        }

        return result.data!!.episodeList.map { ep -> chapterFromObject(ep, result.data.id) }
    }

    protected open fun chapterFromObject(episode: BilibiliEpisodeDto, comicId: Int, isUnlocked: Boolean = false): SChapter = SChapter.create().apply {
        name = buildString {
            if (episode.isPaid && !isUnlocked) {
                append("$EMOJI_LOCKED ")
            }

            append(episode.shortTitle)

            if (episode.title.isNotBlank()) {
                append(" - ${episode.title}")
            }
        }
        date_upload = episode.publicationTime.toDate()
        url = "/mc$comicId/${episode.id}"
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request = imageIndexRequest(chapter.url, "")

    override fun pageListParse(response: Response): List<Page> = imageIndexParse(response)

    @Suppress("SameParameterValue")
    protected open fun imageIndexRequest(chapterUrl: String, credential: String): Request {
        val chapterId = chapterUrl.substringAfterLast("/").toInt()

        val jsonPayload = buildJsonObject {
            put("credential", credential)
            put("ep_id", chapterId)
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapterUrl)
            .build()

        val apiUrl = "$baseUrl/$API_COMIC_V1_COMIC_ENDPOINT/GetImageIndex".toHttpUrl()
            .newBuilder()
            .addCommonParameters()
            .toString()

        return POST(apiUrl, newHeaders, requestBody)
    }

    protected open fun imageIndexParse(response: Response): List<Page> {
        val result = response.parseAs<BilibiliReader>()

        if (result.code != 0) {
            return emptyList()
        }

        val imageQuality = preferences.chapterImageQuality
        val imageFormat = preferences.chapterImageFormat

        val imageUrls = result.data!!.images.map { it.url(imageQuality, imageFormat) }
        val imageTokenRequest = imageTokenRequest(imageUrls)
        val imageTokenResponse = client.newCall(imageTokenRequest).execute()
        val imageTokenResult = imageTokenResponse.parseAs<List<BilibiliPageDto>>()

        return imageTokenResult.data!!
            .mapIndexed { i, page -> Page(i, "", "${page.url}?token=${page.token}") }
    }

    protected open fun imageTokenRequest(urls: List<String>): Request {
        val jsonPayload = buildJsonObject {
            put("urls", json.encodeToString(urls))
        }
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val apiUrl = "$baseUrl/$API_COMIC_V1_COMIC_ENDPOINT/ImageToken".toHttpUrl()
            .newBuilder()
            .addCommonParameters()
            .toString()

        return POST(apiUrl, headers, requestBody)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val imageQualityPref = ListPreference(screen.context).apply {
            key = "${IMAGE_QUALITY_PREF_KEY}_$lang"
            title = intl.imageQualityPrefTitle
            entries = intl.imageQualityPrefEntries
            entryValues = IMAGE_QUALITY_PREF_ENTRY_VALUES
            setDefaultValue(IMAGE_QUALITY_PREF_DEFAULT_VALUE)
            summary = "%s"
        }

        val imageFormatPref = ListPreference(screen.context).apply {
            key = "${IMAGE_FORMAT_PREF_KEY}_$lang"
            title = intl.imageFormatPrefTitle
            entries = IMAGE_FORMAT_PREF_ENTRIES
            entryValues = IMAGE_FORMAT_PREF_ENTRY_VALUES
            setDefaultValue(IMAGE_FORMAT_PREF_DEFAULT_VALUE)
            summary = "%s"
        }

        screen.addPreference(imageQualityPref)
        screen.addPreference(imageFormatPref)
    }

    abstract fun getAllGenres(): Array<BilibiliTag>

    protected open fun getAllAreas(): Array<BilibiliTag> = emptyArray()

    protected open fun getAllSortOptions(): Array<BilibiliTag> = arrayOf(
        BilibiliTag(intl.sortInterest, 0),
        BilibiliTag(intl.sortUpdated, 4),
    )

    protected open fun getAllStatus(): Array<String> =
        arrayOf(intl.statusAll, intl.statusOngoing, intl.statusComplete)

    protected open fun getAllPrices(): Array<String> = emptyArray()

    override fun getFilterList(): FilterList {
        val allAreas = getAllAreas()
        val allPrices = getAllPrices()

        val filters = listOfNotNull(
            StatusFilter(intl.statusLabel, getAllStatus()),
            SortFilter(intl.sortLabel, getAllSortOptions(), defaultPopularSort),
            PriceFilter(intl.priceLabel, getAllPrices()).takeIf { allPrices.isNotEmpty() },
            GenreFilter(intl.genreLabel, getAllGenres()),
            AreaFilter(intl.areaLabel, allAreas).takeIf { allAreas.isNotEmpty() },
        )

        return FilterList(filters)
    }

    private fun expiredImageTokenIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // Get a new image token if the current one expired.
        if (response.code == 403 && chain.request().url.toString().contains(CDN_URL)) {
            response.close()
            val imagePath = chain.request().url.toString()
                .substringAfter(CDN_URL)
                .substringBefore("?token=")
            val imageTokenRequest = imageTokenRequest(listOf(imagePath))
            val imageTokenResponse = chain.proceed(imageTokenRequest)
            val imageTokenResult = imageTokenResponse.parseAs<List<BilibiliPageDto>>()
            imageTokenResponse.close()

            val newPage = imageTokenResult.data!!.first()
            val newPageUrl = "${newPage.url}?token=${newPage.token}"

            val newRequest = imageRequest(Page(0, "", newPageUrl))

            return chain.proceed(newRequest)
        }

        return response
    }

    private val SharedPreferences.chapterImageQuality
        get() = when (getString("${IMAGE_QUALITY_PREF_KEY}_$lang", IMAGE_QUALITY_PREF_DEFAULT_VALUE)!!) {
            "hd" -> "1600w"
            "sd" -> "1000w"
            "low" -> "800w_50q"
            else -> "raw"
        }

    private val SharedPreferences.chapterImageFormat
        get() = getString("${IMAGE_FORMAT_PREF_KEY}_$lang", IMAGE_FORMAT_PREF_DEFAULT_VALUE)!!

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? = firstOrNull { it is R } as? R

    protected open fun HttpUrl.Builder.addCommonParameters(): HttpUrl.Builder = apply {
        if (name == "BILIBILI COMICS") {
            addQueryParameter("lang", apiLang)
            addQueryParameter("sys_lang", apiLang)
        }

        addQueryParameter("device", "pc")
        addQueryParameter("platform", "web")
    }

    protected inline fun <reified T> Response.parseAs(): BilibiliResultDto<T> = use {
        json.decodeFromString(it.body.string())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        const val CDN_URL = "https://manga.hdslb.com"
        const val COVER_CDN_URL = "https://i0.hdslb.com"

        const val API_COMIC_V1_COMIC_ENDPOINT = "twirp/comic.v1.Comic"

        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()

        private const val POPULAR_PER_PAGE = 18
        private const val SEARCH_PER_PAGE = 9

        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^${PREFIX_ID_SEARCH}mc(\\d+)$".toRegex()

        private const val IMAGE_QUALITY_PREF_KEY = "chapterImageQuality"
        private val IMAGE_QUALITY_PREF_ENTRY_VALUES = arrayOf("raw", "hd", "sd", "low")
        private val IMAGE_QUALITY_PREF_DEFAULT_VALUE = IMAGE_QUALITY_PREF_ENTRY_VALUES[1]

        private const val IMAGE_FORMAT_PREF_KEY = "chapterImageFormat"
        private val IMAGE_FORMAT_PREF_ENTRIES = arrayOf("JPG", "WEBP", "PNG")
        private val IMAGE_FORMAT_PREF_ENTRY_VALUES = arrayOf("jpg", "webp", "png")
        private val IMAGE_FORMAT_PREF_DEFAULT_VALUE = IMAGE_FORMAT_PREF_ENTRY_VALUES[0]

        const val THUMBNAIL_RESOLUTION = "@512w.jpg"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        }

        private const val EMOJI_LOCKED = "\uD83D\uDD12"
        const val EMOJI_WARNING = "\u26A0\uFE0F"
    }
}
