package eu.kanade.tachiyomi.extension.zh.hanabimanga

import android.os.Build
import android.util.Log
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getLong
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac.getInstance
import javax.crypto.spec.SecretKeySpec

private const val ANONYMOUS_TOKEN =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVoa3ZxcnhtY2FwZ3Rwc3BnbHJwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM5NjgzMjksImV4cCI6MjA3OTU0NDMyOX0.uuHr888lp14ObW5eWowJrHPJGgQf3sF2l7NPmFN84g4"
private const val COMIC_BODY =
    "id,title,summary,cover_url,release_date,is_finished,authors,region,latest_chapter_title,tags(id,name),categories(id,name)"
private const val APP_VERSION = "2.4.9"
private const val APP_VERSION_CODE = 2040999
private const val CERT_FINGERPRINT = "13a8c9fbdaf19115f39b48dc0f3a7c99568ebb0c91ba1a71218c76f53f7877a8"
private const val NATIVE_SECRET = "7c6cb7f919a688c4c1f5eacf047d97dcd542cae8e3fc84d926160ad879bf3845"
private const val PAGE_SIZE = 20

@Source
abstract class HanabiManga :
    KeiSource(),
    ConfigurableSource {

    override fun getHomeUrl() = "https://web.hanabimanga.com/zh-CN"

    override fun Headers.Builder.configureHeaders() = apply {
        add("apikey", ANONYMOUS_TOKEN)
        removeAll("Referer")
        removeAll("Origin")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TileScrambleInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request).also {
                if (it.code == 401 && request.url.queryParameterNames.containsAll(listOf("t", "sign"))) {
                    it.use { throw IOException("图片链接已过期，请清除章节缓存后重试") }
                }
            }
        }
    }

    private val pref by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context, pref).forEach(screen::addPreference)
    }

    // Customize

    private val signatureKey by lazy { sha256Bytes(NATIVE_SECRET.hexToBytes() + CERT_FINGERPRINT.hexToBytes()) }

    private suspend fun token(): String {
        val httpUrl = getHomeUrl().toHttpUrl()
        val access = client.cookieJar.loadForRequest(httpUrl).find { it.name == "access_token" }
        if (access != null) return access.value

        val refresh = client.cookieJar.loadForRequest(httpUrl).find { it.name == "refresh_token" }
        if (refresh == null) return login()

        val refreshBody = buildJsonObject { put("refresh_token", refresh.value) }
        val response = client.post("$baseUrl/auth/v1/token?grant_type=refresh_token", refreshBody.toJsonRequestBody(), false)

        return if (response.isSuccessful) {
            response.parseAs<JsonObject>().also(::saveTokens).getString("access_token")
        } else {
            login()
        }
    }

    private suspend fun login(): String {
        val email = pref.getString("EMAIL", null)
        val password = pref.getString("PASSWORD", null)
        if (email == null || password == null) return ANONYMOUS_TOKEN

        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            putJsonObject("gotrue_meta_security") {}
        }
        val response = client.post("$baseUrl/auth/v1/token?grant_type=password", body.toJsonRequestBody(), false)
        if (response.code == 400) throw Exception("登录失败，邮箱或密码错误")

        return response.parseAs<JsonObject>().also(::saveTokens).getString("access_token")
    }

    private fun saveTokens(json: JsonObject) {
        val httpUrl = getHomeUrl().toHttpUrl()
        val expiresAt = json.getLong("expires_at") * 1000L
        val c1 = Cookie.Builder().name("access_token").value(json.getString("access_token")).domain(httpUrl.host)
            .expiresAt(expiresAt).build()
        val c2 = Cookie.Builder().name("refresh_token").value(json.getString("refresh_token")).domain(httpUrl.host)
            .expiresAt(expiresAt + 259200000L).build()
        client.cookieJar.saveFromResponse(httpUrl, listOf(c1, c2))
    }

    private fun HttpUrl.Builder.addPaginationParameters(page: Int, order: String) = addQueryParameter("select", COMIC_BODY)
        .addQueryParameter("order", order)
        .addQueryParameter("offset", "${(page - 1) * PAGE_SIZE}")
        .addQueryParameter("limit", PAGE_SIZE.toString())

    private fun HttpUrl.Builder.addSelectParameter(fetchDetails: Boolean, fetchChapters: Boolean) = addQueryParameter(
        "select",
        buildString {
            if (fetchDetails) append(COMIC_BODY)
            if (fetchDetails && fetchChapters) append(',')
            if (fetchChapters) append("chapters(id,idx,title,image_count,category,updated_at)")
        },
    )

    private fun mobilePageRequest(chapter: SChapter): JsonObject {
        val comicId = chapter.memo["cid"]!!.int
        val pages = List(chapter.memo["size"]!!.int) { "%03d".format(Locale.ROOT, it + 1) }
        val timestamp = System.currentTimeMillis() / 1000
        val canonicalPayload = "v1|comic_id=$comicId|chapter_id=${chapter.url}|pages=${pages.joinToString(",")}|timestamp=$timestamp"
        val signature = getInstance("HmacSHA256").run {
            init(SecretKeySpec(signatureKey, this.algorithm))
            doFinal(canonicalPayload.encodeToByteArray()).toHex()
        }
        return buildJsonObject {
            put("comic_id", comicId)
            put("chapter_id", chapter.url)
            putJsonArray("pages") { addAll(pages) }
            put("timestamp", timestamp)
            put("signature", signature)
            putJsonObject("client_diag") {
                put("native_status", "ok")
                put("native_fingerprint_prefix", CERT_FINGERPRINT.take(8))
                put("canonical_payload_sha256", sha256Bytes(canonicalPayload.encodeToByteArray()).toHex())
                put("signature_prefix", signature.take(16))
                put("app_version", APP_VERSION)
                put("app_version_code", APP_VERSION_CODE)
                put("brand", Build.BRAND)
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("product", Build.PRODUCT)
                put("sdk_int", Build.VERSION.SDK_INT)
            }
        }
    }

    private fun sha256Bytes(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)

    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val order = pref.getString(PREF_POPULAR_MANGA, "popularity_daily.desc.nullslast")!!
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addPaginationParameters(page, order).build()
        val comics = client.get(url).parseAs<List<Comic>>()
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Update

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addPaginationParameters(page, "updated_at.desc.nullslast").build()
        val comics = client.get(url).parseAs<List<Comic>>()
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Search

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = if (query.isNotBlank()) {
            val body = mapOf("search_term" to query, "items_per_page" to "$PAGE_SIZE", "page_number" to "$page")
            client.post("$baseUrl/rest/v1/rpc/search_comics_pgroonga", body.toJsonRequestBody()).parseAs<List<Comic>>()
        } else {
            val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().apply {
                addPaginationParameters(page, filters[2].toString())
                if (filters[1].toString().isNotBlank()) addQueryParameter("category_id", "eq.${filters[1]}")
                if (filters[4].toString().isNotBlank()) addQueryParameter("is_finished", "eq.${filters[4]}")
                if (filters[3].toString().isNotBlank()) addQueryParameter("region", "eq.${filters[3]}")
            }
            client.get(url.build()).parseAs<List<Comic>>()
        }
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Manga & Chapter

    override fun getMangaUrl(manga: SManga) = "${getHomeUrl()}/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "${getHomeUrl()}/comic/${chapter.memo["cid"]!!.int}/chapter-${chapter.memo["idx"]!!.int}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl/rest/v1/comics".toHttpUrl().newBuilder().addQueryParameter("id", "eq.${manga.url}")
            .addSelectParameter(fetchDetails, fetchChapters).build()
        val response = client.get(url)
        val comic = response.parseAs<List<Comic>>().first()

        val smanga = if (fetchDetails) comic.toSManga() else manga
        val schapters = if (fetchChapters) {
            comic.chapters!!.sortedWith(compareBy<Chapter> { if (it.category == "volume") 1 else 0 }.thenByDescending { it.idx })
                .map { it.toSChapter(manga.url) }
        } else {
            chapters
        }
        return SMangaUpdate(smanga, schapters)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val body = buildJsonObject {
            put("p_limit", 9)
            put("p_category_id", manga.memo["category_id"]!!.int)
            put("p_exclude_comic_id", manga.url)
        }
        val comics = client.post("$baseUrl/rest/v1/rpc/get_random_comics", body.toJsonRequestBody()).parseAs<List<Comic>>()
        return comics.map(Comic::toSManga)
    }

    // Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.post(
            "$baseUrl/functions/v1/${if (pref.getBoolean(PREF_AI_SR, false)) "vip" else "sd"}-image-url",
            headers.newBuilder().add("Authorization", "Bearer ${token()}").build(),
            mobilePageRequest(chapter).toJsonRequestBody(),
            false,
        )
        if (response.code == 401) response.use { throw Exception("请先在插件设置中登录") }
        if (response.code == 429) {
            when (response.parseAs<JsonObject>().getString("code")) {
                "ANON_QUOTA_EXCEEDED" -> throw Exception("请先在插件设置中登录")
                "FREE_QUOTA_EXCEEDED" -> throw Exception("今日超分额度已用完")
            }
        }
        return response.parseAs<PagesResult>().let { result ->
            if (result.scrambleInfo == null) {
                Log.v("HanabiManga", "移动端 | comicId: ${chapter.memo["cid"]!!.int}, chapterId: ${chapter.url}")
                result.urls.mapIndexed { i, p -> Page(i, imageUrl = p.getString("url")) }
            } else {
                Log.v("HanabiManga", "网页端 | comicId: ${chapter.memo["cid"]!!.int}, chapterId: ${chapter.url}")
                val info = with(result.scrambleInfo) { "$ticket|$nonce|$cols|$rows" }
                result.urls.mapIndexed { i, p -> Page(i, imageUrl = "${p.getString("url")}#$info") }
            }
        }
    }
}
