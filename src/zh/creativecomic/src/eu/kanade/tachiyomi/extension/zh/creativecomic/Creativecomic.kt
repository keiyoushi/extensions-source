package eu.kanade.tachiyomi.extension.zh.creativecomic

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Creativecomic : HttpSource() {
    override val name: String = "CCC追漫台"
    override val lang: String = "zh-Hant"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.creative-comic.tw"
    private val apiUrl = "https://api.creative-comic.tw"
    private var _pageKey: ByteArray? = null
    private var _pageIv: ByteArray? = null
    private var _token: String? = null
    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @SuppressLint("SetJavaScriptEnabled")
    fun getToken(): String {
        _token?.also { return it }
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
                    view!!.evaluateJavascript("window.localStorage.getItem('accessToken')") { token ->
                        webview.stopLoading()
                        webview.destroy()
                        _token = token.removeSurrounding("\"")
                        latch.countDown()
                    }
                }
            }
            webview.loadDataWithBaseURL("$baseUrl/", " ", "text/html", null, null)
        }
        latch.await(10, TimeUnit.SECONDS)
        return _token!!
    }

    private fun getApiHeaders(): Headers {
        val token = getToken()
        if (token == "null") {
            return headersBuilder()
                .add("device: web_desktop")
                .add("uuid: null")
                .build()
        }

        // Check token expiration
        val claims = token.substringAfter(".").substringBefore(".")
        val decoded = Base64.decode(claims, Base64.DEFAULT).decodeToString()
        val expiration = decoded.parseAs<JWTClaims>().exp
        val now = System.currentTimeMillis() / 1000
        if (now > expiration) throw Exception("token过期，请到WebView重新登录")

        return headersBuilder()
            .add("device: web_desktop")
            .add("Authorization: Bearer $token")
            .build()
    }

    private fun getPageKeyIv(): Pair<ByteArray, ByteArray> {
        _pageIv?.also { return Pair(_pageKey!!, _pageIv!!) }
        val token = (getToken().takeUnless { it == "null" } ?: "freeforccc2020reading").toByteArray()
        val md = MessageDigest.getInstance("SHA-512")
        val digest = md.digest(token)
        _pageKey = digest.sliceArray(0..31)
        _pageIv = _pageKey!!.sliceArray(15..30)
        return Pair(_pageKey!!, _pageIv!!)
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::authIntercept)
        .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        if (!url.startsWith("https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/")) {
            return response
        }

        val (key, iv) = request.url.fragment!!.split(":")
        val keyBytes = key.hexStringToByteArray()
        val ivBytes = iv.hexStringToByteArray()
        val cipherBytes = response.body.bytes()
        val cipher = Cipher.getInstance("AES/CBC/PKCS7PADDING")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
        val data = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)

        val image = Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
        val mediaType = data.substringAfter("data:").substringBefore(";").toMediaType()
        val body = image.toResponseBody(mediaType)
        return response.newBuilder().body(body).build()
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/book?page=$page&rows_per_page=24&sort_by=like_count&class=2", getApiHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PopularResponseDto>().data
        val total = data.total
        val page = response.request.url.queryParameter("page")!!.toInt()
        val rowsPerPage = response.request.url.queryParameter("rows_per_page")!!.toInt()
        val hasNextPage = total > page * rowsPerPage
        val mangas = data.data.map {
            it.toSManga()
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/book?page=$page&rows_per_page=24&sort_by=updated_at&class=2", getApiHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            encodedPath("/book")
            addQueryParameter("page", page.toString())
            addQueryParameter("rows_per_page", "12")
            addQueryParameter("keyword", query)
            addQueryParameter("category", "all")
            addQueryParameter("sort_by", "updated_at")
            addQueryParameter("class", "2")
        }.build()
        return GET(url, getApiHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/book/${manga.url}/info", getApiHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<DetailsResponseDto>().data.toSManga()
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiUrl/book/${manga.url}/chapter", getApiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ChapterListResponseDto>().data.chapters.map {
            it.toSChapter()
        }.reversed()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiUrl/book/chapter/${chapter.url}", getApiHeaders())
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PageListResponseDto>().data.chapter.proportion.mapIndexed { index, it ->
            Page(index, it.id.toString())
        }
    }

    override fun imageUrlRequest(page: Page): Request {
        return GET("$apiUrl/book/chapter/image/${page.url}", getApiHeaders())
    }

    override fun imageUrlParse(response: Response): String {
        val encryptedKey = response.parseAs<ImageUrlResponseDto>().data.key
        val (pageKey, pageIv) = getPageKeyIv()
        val decryptedKey = CryptoAES.decrypt(encryptedKey, pageKey, pageIv)
        val id = response.request.url.encodedPathSegments.last()
        return "https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/$id/2#$decryptedKey"
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/zh/book/${manga.url}/content"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/zh/reader_comic/${chapter.url}"
    }

    private fun String.hexStringToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = (
                (Character.digit(this[i], 16) shl 4) +
                    Character.digit(this[i + 1], 16)
                ).toByte()
            i += 2
        }
        return data
    }
}
