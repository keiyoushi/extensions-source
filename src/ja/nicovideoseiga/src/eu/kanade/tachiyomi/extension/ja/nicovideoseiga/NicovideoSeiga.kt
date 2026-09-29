package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.experimental.xor

@Source
abstract class NicovideoSeiga : KeiSource() {

    override val supportsLatest: Boolean = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::imageIntercept)

    private val apiUrl: String = "https://api.nicomanga.jp/api/v1/app/manga"

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        // This is the only API call that doesn't use the API url
        val mangas = client.get("$baseUrl/manga/ajax/ranking?span=total&category=all&page=$page")
            .parseAs<List<PopularManga>>()

        // The api call allows a maximum of 5 pages
        return MangasPage(
            mangas.map { it.toSManga() },
            page < 5,
        )
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val r = client.get("$apiUrl/contents?mode=keyword&sort=score&q=$query&limit=20&offset=${(page - 1) * 20}")
            .parseAs<ApiResponse<Manga>>()
        return MangasPage(r.data.result.map { it.toSManga() }, r.data.extra?.hasNext == true)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/contents/${manga.url}").parseAs<ApiResponse<Manga>>()
                .data.result.first().toSManga()
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$apiUrl/contents/${manga.url}/episodes").parseAs<ApiResponse<Chapter>>()
                .data.result
                // Chapter is unpublished by publishers from Niconico
                // Either due to licensing issues or the publisher is withholding the chapter from selling
                .filter { it.ownership.sellStatus != "publication_finished" }
                .map { it.toSChapter() }
                .sortedByDescending { it.chapter_number }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getMangaUrl(manga: SManga): String {
        // Return functionality to WebView
        return "$baseUrl/comic/${manga.url}"
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/watch/mg${chapter.url}"

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$apiUrl/episodes/${chapter.url}/frames?enable_webp=true", ensureSuccess = false)
        // Nicovideo refuses to serve pages without login only if you are on desktop (Supposedly to provide danmaku)
        // There's no login requirement on the mobile version of the website
        return when (response.code) {
            403 -> {
                response.close()
                // Check if the user is logged in
                // Should return 400 if no session ID is found
                val loginCode = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                    .get("https://account.nicovideo.jp/api/public/v2/user.json", ensureSuccess = false)
                    .use { it.code }
                when (loginCode) {
                    // User needs to purchase the chapter on the official mobile app
                    // Sidenote: Chapters can't be purchased on the site
                    // These paid chapters only show up on the mobile app and are straight up hidden on browsers! Why!?
                    // "Please buy from the official app"
                    200 -> throw SecurityException("公式アプリで購入してください")

                    // User needs to log in via WebView first before accessing the chapter
                    // "Please log in via WebView first"
                    400 -> throw SecurityException("まず、WebViewでログインしてください")

                    else -> throw Exception("HTTP error $loginCode")
                }
            }

            200 -> {
                val r = response.parseAs<ApiResponse<Frame>>()
                // Map the frames to pages
                r.data.result.mapIndexed { i, frame -> Page(i, imageUrl = frame.meta.sourceUrl) }
            }

            else -> {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        // Headers are required to avoid cache miss from server side
        return super.imageRequest(page).newBuilder()
            .header("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .header("pragma", "no-cache")
            .header("cache-control", "no-cache")
            .header("accept-encoding", "gzip, deflate, br")
            .header("sec-fetch-dest", "image")
            .header("sec-fetch-mode", "no-cors")
            .header("sec-fetch-site", "cross-site")
            .header("sec-gpc", "1")
            .build()
    }

    // ============================= Utilities =============================

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        // Intercept requests for paid manga images only
        // Manga images come from 2 sources
        // drm.cdn.nicomanga.jp -> Paid manga (Encrypted)
        // deliver.cdn.nicomanga.jp -> Free manga (Unencrypted)
        val request = chain.request()
        val match = IMAGE_REGEX.find(request.url.toString())
            ?: return chain.proceed(request)

        // Decrypt the image
        val key = match.destructured.component1()
        val response = chain.proceed(request)

        // Retaining ByteArray usage because `getImageType` validation checks magic numbers
        // located at both the head and tail (EOF markers) of the entire image array.
        val encryptedImage = response.body.bytes()
        val decryptedImage = decryptImage(key, encryptedImage)

        // Construct a new response
        val body = decryptedImage.toResponseBody("image/${getImageType(decryptedImage)}".toMediaType())
        return response.newBuilder().body(body).build()
    }

    /**
     * Paid images are xor encrypted in Nicovideo.
     * Take this example:
     * https://drm.cdn.nicomanga.jp/image/d952d4bc53ddcaafffb42d628239ebed4f66df0f_9477/12057916p.webp?1636382474
     *                                    ^^^^^^^^^^^^^^^^
     * The encryption key is stored directly on the URL. Up there. Yes, it stops right there
     * The key is then split into 8 separate bytes
     * Then it cycles through each mini-key and xor with the encrypted image byte by byte
     *          key: d9 52 d4 ... af d9 52 ...
     *                        xor
     *            e: ab cd ef ... 12 34 56 ...
     * The result image is then base64 encoded loaded into the page using the data URI scheme
     * There are additional checks to determine the image type, defaults to webp
     */
    private fun decryptImage(key: String, image: ByteArray): ByteArray {
        val keySet = IntArray(8)
        for (i in 0..7) {
            keySet[i] = key.substring(2 * i).take(2).toInt(16)
        }
        for (i in image.indices) {
            image[i] = image[i] xor keySet[i % 8].toByte()
        }
        return image
    }

    /**
     * Determine the image type by looking at specific bytes for magic numbers
     * This is also how Nicovideo does it
     */
    private fun getImageType(image: ByteArray): String {
        if (image.size < 4) return "webp"
        return if (image[0].toInt() == -1 && image[1].toInt() == -40 && image[image.size - 2].toInt() == -1 && image[image.size - 1].toInt() == -39) {
            "jpeg"
        } else if (image[0].toInt() == -119 && image[1].toInt() == 80 && image[2].toInt() == 78 && image[3].toInt() == 71) {
            "png"
        } else if (image[0].toInt() == 71 && image[1].toInt() == 73 && image[2].toInt() == 70 && image[3].toInt() == 56) {
            "gif"
        } else {
            // It defaults to null in the site, but it's a webp image
            "webp"
        }
    }

    companion object {
        private val IMAGE_REGEX = Regex("https://drm\\.cdn\\.nicomanga\\.jp/image/([a-f0-9]+)_\\d+/\\d+p(\\.[a-z]+)?(\\?\\d+)?")
    }
}
