package eu.kanade.tachiyomi.extension.zh.manhuaren

import android.os.Build
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import javax.crypto.Cipher
import kotlin.random.Random
import kotlin.random.nextUBytes
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Manhuaren :
    KeiSource(),
    ConfigurableSource {

    private val pageSize = 20
    private val baseHttpUrl = baseUrl.toHttpUrl()
    private val preferences by getPreferencesLazy()
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd+HH:mm:ss")
    private val leadingZeros = "^0+".toRegex()
    private val mangaIdRegex = Regex("""DM5_COMIC_MID\s*=\s*(\d+)""")
    private val webHosts = setOf("www.manhuaren.com", "www.dm5.com", "m.dm5.com")

    private val gsnSalt = "4e0a48e1c0b54041bce9c8f0e036124d"
    private val encodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmFCg289dTws27v8GtqIffkP4zgFR+MYIuUIeVO5AGiBV0rfpRh5gg7i8RrT12E9j6XwKoe3xJz1khDnPc65P5f7CJcNJ9A8bj7Al5K4jYGxz+4Q+n0YzSllXPit/Vz/iW5jFdlP6CTIgUVwvIoGEL2sS4cqqqSpCDKHSeiXh9CtMsktc6YyrSN+8mQbBvoSSew18r/vC07iQiaYkClcs7jIPq9tuilL//2uR9kWn5jsp8zHKVjmXuLtHDhM9lObZGCVJwdlN2KDKTh276u/pzQ1s5u8z/ARtK26N8e5w8mNlGcHcHfwyhjfEQurvrnkqYH37+12U3jGk5YNHGyOPcwIDAQAB"
    private val imei by lazy { generateIMEI() }
    private val lastUsedTime by lazy { generateLastUsedTime() }

    companion object {
        private const val WEBSITE_URL = "https://www.manhuaren.com"
        private const val PACKAGE_NAME = "com.ilike.cartoon"
        const val USER_ID_PREF = "userId"
        const val TOKEN_PREF = "token"
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(ErrorResponseInterceptor(baseUrl, preferences))
    }

    private fun randomString(length: Int, pool: String): String = (1..length)
        .map { Random.nextInt(0, pool.length).let { pool[it] } }
        .joinToString("")

    private fun randomNumber(length: Int): String = randomString(length, "0123456789")

    private fun addLuhnCheckDigit(str: String): String {
        var sum = 0
        str.toCharArray().forEachIndexed { i, it ->
            var v = Character.getNumericValue(it)
            sum += if (i % 2 == 0) {
                v
            } else {
                v *= 2
                if (v < 10) {
                    v
                } else {
                    v - 9
                }
            }
        }
        var checkDigit = sum % 10
        if (checkDigit != 0) {
            checkDigit = 10 - checkDigit
        }

        return "$str$checkDigit"
    }

    private fun generateIMEI(): String = addLuhnCheckDigit(randomNumber(14))

    private suspend fun fetchToken(): String {
        var token = preferences.getString(TOKEN_PREF, "")!!
        var userId = preferences.getString(USER_ID_PREF, "")!!
        if (token.isEmpty() || userId.isEmpty()) {
            val response = client.newCall(getAnonyUser()).await()
                .parseAs<ManhuarenResponse<TokenResponse>>().response

            token = "${response.tokenResult.scheme} ${response.tokenResult.parameter}"
            userId = response.userId.toString()

            preferences.edit().apply {
                putString(TOKEN_PREF, token)
                putString(USER_ID_PREF, userId)
            }.apply()
        }

        return token
    }

    private fun generateLastUsedTime(): String = ((Date().time / 1000) * 1000).toString()

    private fun encrypt(message: String): String {
        val x509EncodedKeySpec = X509EncodedKeySpec(Base64.decode(encodedPublicKey, Base64.DEFAULT))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(x509EncodedKeySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        return Base64.encodeToString(cipher.doFinal(message.toByteArray()), Base64.NO_WRAP)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getAnonyUser(): Request {
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("v1/user/createAnonyUser2")
            .build()

        val androidId = Random.nextUBytes(8)
            .joinToString("") { it.toString(16).padStart(2, '0') }
            .replaceFirst(leadingZeros, "")
            .uppercase()

        val body = AnonyUserRequest(
            listOf(
                AnonyUserKey(encrypt(imei), "0"),
                AnonyUserKey(encrypt(androidId), "2"),
                AnonyUserKey(encrypt(UUID.randomUUID().toString()), "-1"),
            ),
        )

        return myPost(url, body.toJsonRequestBody())
    }

    private fun addGsnHash(request: Request): Request {
        val isPost = request.method == "POST"

        val params = request.url.queryParameterNames.toMutableSet()
        val bodyBuffer = Buffer()
        if (isPost) {
            params.add("body")
            request.body?.writeTo(bodyBuffer)
        }

        var str = gsnSalt + request.method
        params.toSortedSet().forEach {
            if (it != "gsn") {
                val value = if (isPost && it == "body") bodyBuffer.readUtf8() else request.url.queryParameter(it)
                str += "$it${urlEncode(value)}"
            }
        }
        str += gsnSalt

        val gsn = str.encodeUtf8().md5().hex()
        val newUrl = request.url.newBuilder()
            .addQueryParameter("gsn", gsn)
            .build()

        return request.newBuilder()
            .url(newUrl)
            .build()
    }

    private fun myRequest(url: HttpUrl, method: String, body: RequestBody?): Request {
        val now = timeFormat.format(LocalDateTime.now())
        val userId = preferences.getString(USER_ID_PREF, "-1")!!
        val newUrl = url.newBuilder()
            .setQueryParameter("gsm", "md5")
            .setQueryParameter("gft", "json")
            .setQueryParameter("gak", "android_manhuaren2")
            .setQueryParameter("gat", "")
            .setQueryParameter("gui", userId)
            .setQueryParameter("gts", now)
            .setQueryParameter("gut", "0") // user type
            .setQueryParameter("gem", "1")
            .setQueryParameter("gaui", userId)
            .setQueryParameter("gln", "") // location
            .setQueryParameter("gcy", "US") // country
            .setQueryParameter("gle", "zh") // language
            .setQueryParameter("gcl", "dm5") // Umeng channel
            .setQueryParameter("gos", "1") // OS (int)
            .setQueryParameter("gov", "33_13") // "{Build.VERSION.SDK_INT}_{Build.VERSION.RELEASE}"
            .setQueryParameter("gav", "7.0.1") // app version
            .setQueryParameter("gdi", imei)
            .setQueryParameter("gfcl", "dm5") // Umeng channel config
            .setQueryParameter("gfut", lastUsedTime) // first used time
            .setQueryParameter("glut", lastUsedTime) // last used time
            .setQueryParameter("gpt", PACKAGE_NAME) // package name
            .setQueryParameter("gciso", "us") // https://developer.android.com/reference/android/telephony/TelephonyManager#getSimCountryIso()
            .setQueryParameter("glot", "") // longitude
            .setQueryParameter("glat", "") // latitude
            .setQueryParameter("gflot", "") // first location longitude
            .setQueryParameter("gflat", "") // first location latitude
            .setQueryParameter("glbsaut", "0") // is allow location (0 or 1)
            .setQueryParameter("gac", "") // area code
            .setQueryParameter("gcut", "GMT+8") // time zone
            .setQueryParameter("gfcc", "") // first country code
            .setQueryParameter("gflg", "") // first language
            .setQueryParameter("glcn", "") // country name
            .setQueryParameter("glcc", "") // country code
            .setQueryParameter("gflcc", "") // first location country code
            .build()

        return addGsnHash(
            Request.Builder()
                .method(method, body)
                .url(newUrl)
                .headers(headers)
                .build(),
        )
    }

    private fun myPost(url: HttpUrl, body: RequestBody?): Request = myRequest(url, "POST", body).newBuilder()
        .cacheControl(CacheControl.Builder().noCache().noStore().build())
        .build()

    private suspend fun myGet(url: HttpUrl): Response {
        val authorization = fetchToken()
        val request = myRequest(url, "GET", null).newBuilder()
            .addHeader("Authorization", authorization)
            .cacheControl(CacheControl.Builder().maxAge(10.minutes).build())
            .build()
        return client.newCall(request).await()
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        val yqci = buildJsonObject {
            put("at", -1)
            put("av", "7.0.1") // app version
            put("ciso", "us") // https://developer.android.com/reference/android/telephony/TelephonyManager#getSimCountryIso()
            put("cl", "dm5") // Umeng channel
            put("cy", "US") // country
            put("di", imei)
            put("dm", Build.MODEL)
            put("fcl", "dm5") // Umeng channel config
            put("ft", "mhr") // from type
            put("fut", lastUsedTime) // first used time
            put("installation", "dm5")
            put("le", "zh") // language
            put("ln", "") // location
            put("lut", lastUsedTime) // last used time
            put("nt", 3)
            put("os", 1) // OS (int)
            put("ov", "33_13") // "{Build.VERSION.SDK_INT}_{Build.VERSION.RELEASE}"
            put("pt", PACKAGE_NAME) // package name
            put("rn", "1080x1920") // screen "{width}x{height}"
            put("st", 0)
        }
        val yqpp = buildJsonObject {
            put("ciso", "us") // https://developer.android.com/reference/android/telephony/TelephonyManager#getSimCountryIso()
            put("laut", "0") // is allow location ("0" or "1")
            put("lot", "") // longitude
            put("lat", "") // latitude
            put("cut", "GMT+8") // time zone
            put("fcc", "") // first country code
            put("flg", "") // first language
            put("lcc", "") // country code
            put("lcn", "") // country name
            put("flcc", "") // first location country code
            put("flot", "") // first location longitude
            put("flat", "") // first location latitude
            put("ac", "") // area code
        }

        val userId = preferences.getString(USER_ID_PREF, "-1")!!
        add("X-Yq-Yqci", yqci.toString())
        add("X-Yq-Key", userId)
        add("yq_is_anonymous", "1")
        add("x-request-id", UUID.randomUUID().toString())
        add("X-Yq-Yqpp", yqpp.toString())
    }

    private fun urlEncode(str: String?): String = URLEncoder.encode(str ?: "", "UTF-8")
        .replace("+", "%20")
        .replace("%7E", "~")
        .replace("*", "%2A")

    private fun mangasPageParse(response: Response): MangasPage = response.parseAs<ManhuarenResponse<MangaListDto>>().response.toMangasPage()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseHttpUrl.newBuilder()
            .addQueryParameter("subCategoryType", "0")
            .addQueryParameter("subCategoryId", "0")
            .addQueryParameter("start", (pageSize * (page - 1)).toString())
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("sort", "0")
            .addPathSegments("v2/manga/getCategoryMangas")
            .build()
        return mangasPageParse(myGet(url))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseHttpUrl.newBuilder()
            .addQueryParameter("subCategoryType", "0")
            .addQueryParameter("subCategoryId", "0")
            .addQueryParameter("start", (pageSize * (page - 1)).toString())
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("sort", "1")
            .addPathSegments("v2/manga/getCategoryMangas")
            .build()
        return mangasPageParse(myGet(url))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var url = baseHttpUrl.newBuilder()
            .addQueryParameter("start", (pageSize * (page - 1)).toString())
            .addQueryParameter("limit", pageSize.toString())
        if (query != "") {
            url = url.addQueryParameter("keywords", query)
                .addPathSegments("v1/search/getSearchManga")
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        url = url.setQueryParameter("sort", filter.getId())
                    }

                    is CategoryFilter -> {
                        url = url.setQueryParameter("subCategoryId", filter.getId())
                            .setQueryParameter("subCategoryType", filter.getType())
                    }

                    else -> {}
                }
            }
            url = url.addPathSegments("v2/manga/getCategoryMangas")
        }
        return mangasPageParse(myGet(url.build()))
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = myGet((baseUrl + manga.url).toHttpUrl())
            .parseAs<ManhuarenResponse<MangaDetailDto>>().response
        if (fetchDetails) {
            dto.toSManga(manga)
        }
        val chapterList = if (fetchChapters) dto.toChapterList() else chapters
        return SMangaUpdate(manga, chapterList)
    }

    override fun getHomeUrl(): String = "$WEBSITE_URL/"

    override fun getMangaUrl(manga: SManga): String = "$WEBSITE_URL/showcomic/?id=" + (baseUrl + manga.url).toHttpUrl().queryParameter("mangaId").orEmpty()

    override fun getChapterUrl(chapter: SChapter): String = "$WEBSITE_URL/m" + (baseUrl + chapter.url).toHttpUrl().queryParameter("mangaSectionId").orEmpty() + "/"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in webHosts) return null
        val id = url.queryParameter("id")
            ?: client.newCall(GET(url.toString(), headers)).await().body.string()
                .let { mangaIdRegex.find(it)?.groupValues?.get(1) }
            ?: return null
        val manga = SManga.create().apply { this.url = "/v1/manga/getDetail?mangaId=$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl().newBuilder()
            .addQueryParameter("netType", "4")
            .addQueryParameter("loadreal", "1")
            .addQueryParameter("imageQuality", "2")
            .build()
        return myGet(url).parseAs<ManhuarenResponse<PageListDto>>().response.toPageList()
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "http://www.dm5.com/dm5api/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = USER_ID_PREF
            title = "用户ID"
            val userId = preferences.getString(USER_ID_PREF, "")!!
            summary = userId.ifEmpty { "无用户ID，点击设置" }
            setOnPreferenceChangeListener { _, newValue ->
                summary = (newValue as String).ifEmpty { "无用户ID，点击设置" }
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = TOKEN_PREF
            title = "令牌(Token)"
            val token = preferences.getString(TOKEN_PREF, "")!!
            summary = if (token.isEmpty()) "无令牌，点击设置" else "点击查看"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String).isEmpty()) "无令牌，点击设置" else "点击查看"
                true
            }
        }.let(screen::addPreference)
    }
}
