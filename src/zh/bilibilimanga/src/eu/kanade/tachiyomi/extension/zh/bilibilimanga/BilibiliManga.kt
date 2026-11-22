package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.ece.Ece
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.TimeZone
import kotlin.random.Random
import kotlin.time.Clock

@Source
abstract class BilibiliManga :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
        addInterceptor(ImageInterceptor(keyPair.private))
        addCookie {
            val buvid3 = preferences.getString(BUVID3_PREF, null) ?: return@addCookie emptyList()
            listOf("buvid3" to buvid3, "buvid4" to preferences.getString(BUVID4_PREF, "")!!)
        }
    }

    override suspend fun getPopularManga(page: Int) = classPage(page, order = 0)

    override suspend fun getLatestUpdates(page: Int) = classPage(page, order = 1)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val body = SearchBody(query, page, PAGE_SIZE).toJsonString()
            val from = baseUrl.toHttpUrl().newBuilder().addPathSegment("search").addQueryParameter("keyword", query).build()
            val result = twirp("Search", body, from.toString()).parseAs<SearchDto>()
            return MangasPage(result.list.map { it.toSManga() }, page * PAGE_SIZE < result.totalNum)
        }
        return classPage(
            page,
            styleId = filters.firstInstanceOrNull<StyleFilter>()?.id ?: -1,
            areaId = filters.firstInstanceOrNull<AreaFilter>()?.id ?: -1,
            isFinish = filters.firstInstanceOrNull<StatusFilter>()?.id ?: -1,
            order = filters.firstInstanceOrNull<OrderFilter>()?.id ?: 0,
            specialTag = filters.firstInstanceOrNull<SpecialFilter>()?.id ?: 0,
            isFree = filters.firstInstanceOrNull<PriceFilter>()?.id ?: -1,
        )
    }

    // -1 (0 for specialTag) is the site's "all".
    private suspend fun classPage(
        page: Int,
        order: Int,
        styleId: Int = -1,
        areaId: Int = -1,
        isFinish: Int = -1,
        specialTag: Int = 0,
        isFree: Int = -1,
    ): MangasPage {
        val body = ClassPageBody(styleId, areaId, isFinish, order, specialTag, page, PAGE_SIZE, isFree, m2).toJsonString()
        val items = twirp("ClassPage", body, "/classify", styleId.toString()).parseAs<List<ClassPageItemDto>>()
        // Pages can come back short while more follow.
        return MangasPage(items.map { it.toSManga() }, items.isNotEmpty())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.firstNotNullOfOrNull { COMIC_SEGMENT.matchEntire(it)?.groupValues?.get(1) } ?: return null
        return comicDetail(id.toInt()).toSManga(baseUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = comicDetail(manga.url.substringAfter("/mc").toInt())
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)
        val episodes = comic.episodes.filter { showLocked || !it.isPaywalled }
        return SMangaUpdate(comic.toSManga(baseUrl), episodes.map { it.toSChapter(comic.id) })
    }

    private suspend fun comicDetail(id: Int): ComicDto {
        val body = ComicIdBody(id).toJsonString()
        return twirp("ComicDetail", body, "/detail/mc$id", id.toString()).parseAs<ComicDto>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val epId = chapter.url.substringAfterLast("/").toInt()
        val body = ImageIndexBody(epId, m2).toJsonString()
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val images = twirp("GetImageIndex", body, chapterUrl.toString(), epId.toString()).parseAs<ImageIndexDto>().images
        // The image path rides as the fragment: ImageToken is asked per page, its tokens expire in minutes.
        return images.mapIndexed { i, image -> Page(i, url = chapterUrl.newBuilder().fragment(image.path).build().toString()) }
    }

    override suspend fun getImageUrl(page: Page): String {
        val pageUrl = page.url.toHttpUrl()
        val urls = listOf(pageUrl.fragment + preferences.getString(IMAGE_QUALITY_PREF, IMAGE_QUALITY_DEFAULT)).toJsonString()
        val body = ImageTokenBody(urls, m1).toJsonString()
        // The urls field doubles as ImageToken's id field for the response key.
        val token = twirp("ImageToken", body, pageUrl.newBuilder().fragment(null).build().toString(), urls)
            .parseAs<List<ImageTokenDto>>().single()
        return token.completeUrl.toHttpUrl().newBuilder().addQueryParameter("code", "DanmakuInfo").build().toString()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = twirp("AllLabel", "{}", "/classify")

    override fun getFilterList(data: JsonElement?): FilterList {
        val labels = data?.parseAs<LabelsDto>() ?: return FilterList()
        return FilterList(
            Filter.Header("搜索文字时忽略筛选"),
            OrderFilter(labels.orders),
            StyleFilter(labels.styles),
            AreaFilter(labels.areas),
            StatusFilter(labels.status),
            PriceFilter(labels.prices),
            SpecialFilter(labels.special),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "显示付费章节"
            summary = "需购买的章节以 🔒 标记。已购章节在 WebView 登录后可读。"
            setDefaultValue(false)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "图片质量"
            entries = arrayOf("标准（网页阅读器默认，1100px）", "原图（约 2000px，每页数 MB）")
            entryValues = arrayOf(IMAGE_QUALITY_DEFAULT, "")
            summary = "%s"
            setDefaultValue(IMAGE_QUALITY_DEFAULT)
        }.also(screen::addPreference)
    }

    // idField: the request's id field (comic_id, ep_id, urls), given for the signed methods, whose responses come encrypted.
    private suspend fun twirp(method: String, body: String, from: String, idField: String? = null): JsonElement {
        val buvid3 = buvid3()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("twirp/comic.v1.Comic/$method")
            .addQueryParameter("device", "pc")
            .addQueryParameter("platform", "web")
            .apply { if (idField != null) addQueryParameter("ultra_sign", UltraSign.sign(body, Clock.System.now().toEpochMilliseconds())) }
            .addQueryParameter("nov", "27")
            .addQueryParameter("a", "810")
            .build()
        val apiHeaders = headers.newBuilder().apply { if (idField != null) set("x-bili-data-sn", DATA_SN) }.build()
        val response = client.post(url, apiHeaders, Fetch.XHR.from(from), body.toRequestBody(JSON_MEDIA_TYPE)).parseAs<ResponseDto>()
        if (response.code != 0) throw Exception(response.msg.ifEmpty { "Error ${response.code}" })
        val encrypted = response.bytesData ?: return response.data!!
        return BytesData.decrypt(Base64.decode(encrypted, Base64.DEFAULT), buvid3, idField!!).parseAs<JsonElement>()
    }

    private val buvidLock = Mutex()

    private suspend fun buvid3(): String = buvidLock.withLock {
        preferences.getString(BUVID3_PREF, null)?.let { return it }
        val buvid = client.get(BUVID_URL, Fetch.XHR.from(baseUrl)).parseAs<BuvidDto>().data
        preferences.edit().putString(BUVID3_PREF, buvid.buvid3).putString(BUVID4_PREF, buvid.buvid4).apply()
        buvid.buvid3
    }

    // The reader's P-256 key; the server encrypts images for its public key (m1).
    private val keyPair by lazy {
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }

    private val m1 by lazy {
        Base64.encodeToString(Ece.publicKeyToRaw(keyPair.public as ECPublicKey), Base64.NO_WRAP)
    }

    private val m2: String get() {
        val seed = preferences.getLong(M2_SEED_PREF, 0L).takeIf { it != 0L }
            ?: Random.nextLong().also { preferences.edit().putLong(M2_SEED_PREF, it).apply() }
        val fingerprint = M2.fingerprint(headers["User-Agent"].orEmpty(), headers["Accept-Language"], TimeZone.getDefault().id, seed)
        return M2.fromBase64(Base64.encodeToString(fingerprint.encodeToByteArray(), Base64.NO_WRAP))
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val BUVID_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val DATA_SN = "1E74C20E5720FBF3BB351965D7A9DFC1"
        private const val BUVID3_PREF = "buvid3"
        private const val BUVID4_PREF = "buvid4"
        private const val M2_SEED_PREF = "m2Seed"
        private const val SHOW_LOCKED_PREF = "showLockedChapters"
        private const val IMAGE_QUALITY_PREF = "imageQuality"
        private const val IMAGE_QUALITY_DEFAULT = "@1100w.avif"
        private val COMIC_SEGMENT = Regex("""mc(\d+)""")
    }
}
