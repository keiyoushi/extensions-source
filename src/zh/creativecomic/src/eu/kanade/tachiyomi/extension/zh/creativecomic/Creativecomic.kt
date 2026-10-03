package eu.kanade.tachiyomi.extension.zh.creativecomic

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Creativecomic : KeiSource() {
    private val apiUrl = "https://api.creative-comic.tw"
    private var pageKey: ByteArray? = null
    private var pageIv: ByteArray? = null
    private var token: String? = null
    private var tokenFetched = false

    private suspend fun getToken(): String? {
        if (!tokenFetched) {
            token = getLocalStorage("$baseUrl/", "accessToken")
            tokenFetched = true
        }
        return token
    }

    private suspend fun getApiHeaders(): Headers {
        val token = getToken()
            ?: return headers.newBuilder()
                .add("device", "web_desktop")
                .add("uuid", "null")
                .build()

        // Check token expiration
        val claims = token.substringAfter(".").substringBefore(".")
        val decoded = Base64.decode(claims, Base64.DEFAULT).decodeToString()
        val expiration = decoded.parseAs<JWTClaims>().exp
        val now = System.currentTimeMillis() / 1000
        if (now > expiration) throw Exception("token过期，请到WebView重新登录")

        return headers.newBuilder()
            .add("device", "web_desktop")
            .add("Authorization", "Bearer $token")
            .build()
    }

    private suspend fun getPageKeyIv(): Pair<ByteArray, ByteArray> {
        pageIv?.also { return Pair(pageKey!!, pageIv!!) }
        val token = (getToken() ?: "freeforccc2020reading").toByteArray()
        val md = MessageDigest.getInstance("SHA-512")
        val digest = md.digest(token)
        pageKey = digest.sliceArray(0..31)
        pageIv = pageKey!!.sliceArray(15..30)
        return Pair(pageKey!!, pageIv!!)
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authIntercept)

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        if (!url.startsWith("https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/")) {
            return response
        }

        val (key, iv) = request.url.fragment!!.split(":")
        val keyBytes = key.decodeHex()
        val ivBytes = iv.decodeHex()
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

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val data = client.get(url, getApiHeaders()).parseAs<PopularResponseDto>().data
        val page = url.queryParameter("page")!!.toInt()
        val rowsPerPage = url.queryParameter("rows_per_page")!!.toInt()
        val hasNextPage = data.total > page * rowsPerPage
        val mangas = data.data.map {
            it.toSManga()
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$apiUrl/book?page=$page&rows_per_page=24&sort_by=like_count&class=2".toHttpUrl())

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$apiUrl/book?page=$page&rows_per_page=24&sort_by=updated_at&class=2".toHttpUrl())

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            encodedPath("/book")
            addQueryParameter("page", page.toString())
            addQueryParameter("rows_per_page", "12")
            addQueryParameter("keyword", query)
            addQueryParameter("category", "all")
            addQueryParameter("sort_by", "updated_at")
            addQueryParameter("class", "2")
        }.build()
        return getMangaList(url)
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/book/${manga.url}/info", getApiHeaders())
                .parseAs<DetailsResponseDto>().data.toSManga()
                .apply { url = manga.url }
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$apiUrl/book/${manga.url}/chapter", getApiHeaders())
                .parseAs<ChapterListResponseDto>().data.chapters
                .map { it.toSChapter() }
                .reversed()
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/book/chapter/${chapter.url}", getApiHeaders())
        .parseAs<PageListResponseDto>().data.chapter.proportion.mapIndexed { index, it ->
            Page(index, it.id.toString())
        }

    override suspend fun getImageUrl(page: Page): String {
        val encryptedKey = client.get("$apiUrl/book/chapter/image/${page.url}", getApiHeaders())
            .parseAs<ImageUrlResponseDto>().data.key
        val (pageKey, pageIv) = getPageKeyIv()
        val decryptedKey = CryptoAES.decrypt(encryptedKey, pageKey, pageIv)
        return "https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/${page.url}/2#$decryptedKey"
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/zh/book/${manga.url}/content"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/zh/reader_comic/${chapter.url}"
}
