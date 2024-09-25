package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.experimental.xor

class NicovideoSeiga : HttpSource() {
    override val baseUrl: String = "https://sp.manga.nicovideo.jp"
    override val lang: String = "ja"
    override val name: String = "Nicovideo Seiga"
    override val supportsLatest: Boolean = false
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()
    override val versionId: Int = 2
    private val apiUrl: String = "https://api.nicomanga.jp/api/v1/app/manga"
    private val json: Json by injectLazy()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val pageNumber = response.request.url.queryParameter("page")!!.toInt()
        val mangas = json.decodeFromString<List<PopularManga>>(response.body.string())
        // The api call allows a maximum of 5 pages
        return MangasPage(
            mangas.map {
                SManga.create().apply {
                    title = it.title
                    author = it.author
                    // The thumbnail provided only displays a glimpse of the latest chapter. Not the actual cover
                    // We can obtain a better thumbnail when the user clicks into the details
                    thumbnail_url = it.thumbnailUrl
                    // Store id only as we override the url down the chain
                    url = it.id.toString()
                }
            },
            pageNumber < 5,
        )
    }

    override fun popularMangaRequest(page: Int): Request =
        // This is the only API call that doesn't use the API url
        GET("$baseUrl/manga/ajax/ranking?span=total&category=all&page=$page", headers)

    // Parses the common manga entry object from the api
    private fun parseMangaEntry(entry: Manga): SManga {
        return SManga.create().apply {
            title = entry.meta.title
            // The description is html. Simply using Jsoup to remove all the html tags
            description = Jsoup.parse(entry.meta.description).wholeText()
            // Although their API does contain individual author fields, they are arbitrary strings and we can't trust it conforms to a format
            // Use display name instead which puts all of the people involved together
            author = entry.meta.author
            thumbnail_url = entry.meta.thumbnailUrl
            // Store id only as we override the url down the chain
            url = entry.id.toString()
            status = when (entry.meta.serialStatus) {
                "serial" -> SManga.ONGOING
                "concluded" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val r = json.decodeFromString<ApiResponse<Manga>>(response.body.string())
        return MangasPage(r.data.result.map { parseMangaEntry(it) }, r.data.extra!!.hasNext!!)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$apiUrl/contents?mode=keyword&sort=score&q=$query&limit=20&offset=${(page - 1) * 20}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val r = json.decodeFromString<ApiResponse<Manga>>(response.body.string())
        return parseMangaEntry(r.data.result.first())
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Overwrite to use the API instead of scraping the shared URL
        return GET("$apiUrl/contents/${manga.url}", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        // Return functionality to WebView
        return "$baseUrl/comic/${manga.url}"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val r = json.decodeFromString<ApiResponse<Chapter>>(response.body.string())
        return r.data.result
            // Chapter is unpublished by publishers from Niconico
            // Either due to licensing issues or the publisher is withholding the chapter from selling
            .filter { it.ownership.sellStatus != "publication_finished" }
            .map { chapter ->
                SChapter.create().apply {
                    val prefix = when (chapter.ownership.sellStatus) {
                        "selling" -> "\uD83D\uDCB4 "
                        "pre_selling" -> "\u23F3\uD83D\uDCB4 "
                        else -> ""
                    }
                    name = prefix + chapter.meta.title
                    // Timestamp is in seconds, convert to milliseconds
                    date_upload = chapter.meta.createdAt * 1000
                    // While chapters are properly sorted, authors often add promotional material as "chapters" which breaks trackers
                    // There's no way to properly filter these as they are treated the same as normal chapters
                    chapter_number = chapter.meta.number.toFloat()
                    // Store id only as we override the url down the chain
                    url = chapter.id.toString()
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Overwrite to use the API instead of scraping the shared URL
        return GET("$apiUrl/contents/${manga.url}/episodes", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/watch/mg${chapter.url}"
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable()
            .flatMap { response ->
                // Nicovideo refuses to serve pages without login only if you are on desktop (Supposedly to provide danmaku)
                // There's no login requirement on the mobile version of the website
                when (response.code) {
                    403 -> {
                        // Check if the user is logged in
                        // This is the account page. You get 302 if you are not logged in
                        client.newBuilder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build()
                            .newCall(GET("https://www.nicovideo.jp/my"))
                            .asObservable()
                            .flatMap { login ->
                                when (login.code) {
                                    200 -> {
                                        // User needs to purchase the chapter on the official mobile app
                                        // Sidenote: Chapters can't be purchased on the site
                                        // These paid chapters only show up on the mobile app and are straight up hidden on browsers! Why!?
                                        // "Please buy from the official app"
                                        Observable.error(SecurityException("公式アプリで購入してください"))
                                    }

                                    302 -> {
                                        // User needs to login via WebView first before accessing the chapter
                                        // "Please login via WebView first"
                                        Observable.error(SecurityException("まず、WebViewでログインしてください"))
                                    }

                                    else -> Observable.error(Exception("HTTP error ${login.code}"))
                                }
                            }
                    }

                    200 -> Observable.just(pageListParse(response))
                    else -> Observable.error(Exception("HTTP error ${response.code}"))
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val r = json.decodeFromString<ApiResponse<Frame>>(response.body.string())
        // Map the frames to pages
        return r.data.result.mapIndexed { i, frame -> Page(i, frame.meta.sourceUrl, frame.meta.sourceUrl) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Overwrite to use the API instead of scraping the shared URL
        return GET("$apiUrl/episodes/${chapter.url}/frames?enable_webp=true", headers)
    }

    override fun imageRequest(page: Page): Request {
        // Headers are required to avoid cache miss from server side
        val headers = headersBuilder()
            .set("referer", "$baseUrl/")
            .set("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("pragma", "no-cache")
            .set("cache-control", "no-cache")
            .set("accept-encoding", "gzip, deflate, br")
            .set("sec-fetch-dest", "image")
            .set("sec-fetch-mode", "no-cors")
            .set("sec-fetch-site", "cross-site")
            .set("sec-gpc", "1")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        // Intercept requests for paid manga images only
        // Manga images come from 2 sources
        // drm.cdn.nicomanga.jp -> Paid manga (Encrypted)
        // deliver.cdn.nicomanga.jp -> Free manga (Unencrypted)
        val imageRegex =
            Regex("https://drm.cdn.nicomanga.jp/image/([a-f0-9]+)_\\d{4}/\\d+p(\\.[a-z]+)?(\\?\\d+)?")
        val match = imageRegex.find(chain.request().url.toUrl().toString())
            ?: return chain.proceed(chain.request())

        // Decrypt the image
        val key = match.destructured.component1()
        val response = chain.proceed(chain.request())
        val encryptedImage = response.body.bytes()
        val decryptedImage = decryptImage(key, encryptedImage)

        // Construct a new response
        val body =
            decryptedImage.toResponseBody("image/${getImageType(decryptedImage)}".toMediaType())
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
        for (i in 0..7)
            keySet[i] = key.substring(2 * i).take(2).toInt(16)
        for (i in image.indices)
            image[i] = image[i] xor keySet[i % 8].toByte()
        return image
    }

    /**
     * Determine the image type by looking at specific bytes for magic numbers
     * This is also how Nicovideo does it
     */
    private fun getImageType(image: ByteArray): String {
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
}
