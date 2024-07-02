package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data.Chapter
import eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data.Frame
import eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data.Manga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import kotlin.experimental.xor

class NicovideoSeiga : HttpSource() {
    override val baseUrl: String = "https://sp.manga.nicovideo.jp"
    override val lang: String = "ja"
    override val name: String = "Nicovideo Seiga"
    override val supportsLatest: Boolean = false
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()
    private val apiUrl: String = "https://api.nicomanga.jp/api/v1/app/manga"
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val pageNumber = response.request.url.queryParameter("page")!!.toInt()
        val r = json.parseToJsonElement(response.body.string()).jsonArray
        val mangas = ArrayList<SManga>()
        for (entry in r) {
            val mangaEntry = entry as JsonObject
            val id = mangaEntry["id"]!!.jsonPrimitive.int
            mangas.add(
                SManga.create().apply {
                    title = mangaEntry["title"]!!.jsonPrimitive.content
                    author = mangaEntry["author"]!!.jsonPrimitive.content
                    // The thumbnail provided only displays a glimpse of the latest chapter. Not the actual cover
                    // We can obtain a better thumbnail when the user clicks into the details
                    thumbnail_url = mangaEntry["thumbnail_url"]!!.jsonPrimitive.content
                    setUrlWithoutDomain("$baseUrl/comic/$id")
                },
            )
        }
        // The api call allows a maximum of 5 pages
        return MangasPage(mangas, pageNumber < 5)
    }

    override fun popularMangaRequest(page: Int): Request =
        // This is the only API call that doesn't use the API url
        GET("$baseUrl/manga/ajax/ranking?span=total&category=all&page=$page")

    // Parses the common manga entry object from the api
    private fun parseMangaEntry(entry: Manga): SManga {
        // The description is html. Simply using Jsoup to remove all the html tags
        val descriptionText = Jsoup.parse(entry.meta.description).text()
        return SManga.create().apply {
            title = entry.meta.title
            description = descriptionText
            // Although their API does contain individual author fields, they are arbitrary strings and we can't trust it conforms to a format
            // Use display name instead which puts all of the people involved together
            author = entry.meta.author
            thumbnail_url = entry.meta.thumbnailUrl
            // Use the share URL so that WebView still works
            setUrlWithoutDomain(entry.meta.shareUrl)
            status = when (entry.meta.serialStatus) {
                "serial" -> SManga.ONGOING
                "concluded" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val r = json.parseToJsonElement(response.body.string()).jsonObject
        val hasNext =
            r["data"]!!.jsonObject["extra"]!!.jsonObject["has_next"]!!.jsonPrimitive.boolean
        val result = r["data"]!!.jsonObject["result"]!!.jsonArray
        val mangas = ArrayList<SManga>()
        for (entry in result) {
            val manga = json.decodeFromJsonElement<Manga>(entry)
            mangas.add(parseMangaEntry(manga))
        }
        return MangasPage(mangas, hasNext)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$apiUrl/contents?mode=keyword&sort=score&q=$query&limit=20&offset=${(page - 1) * 20}")

    override fun mangaDetailsParse(response: Response): SManga {
        val r = json.parseToJsonElement(response.body.string()).jsonObject
        val entry = json.decodeFromJsonElement<Manga>(r["data"]!!.jsonObject["result"]!!)
        return parseMangaEntry(entry)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Overwrite to use the API instead of scraping the shared URL
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/contents/$id")
    }

    override fun getMangaUrl(manga: SManga): String {
        // Return functionality to WebView
        return "$baseUrl/comic/${manga.url.substringAfterLast("/")}"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val r = json.parseToJsonElement(response.body.string()).jsonObject
        val result = r["data"]!!.jsonObject["result"]!!.jsonArray
        val chapters = ArrayList<SChapter>()
        for (entry in result) {
            val chapter = json.decodeFromJsonElement<Chapter>(entry)
            val isPaid = chapter.ownership.sellStatus == "selling"
            if (chapter.ownership.sellStatus == "publication_finished") {
                // Chapter is unpublished by publishers from Niconico
                // Either due to licensing issues or the publisher is withholding the chapter from selling
                continue
            }
            chapters.add(
                SChapter.create().apply {
                    name = (if (isPaid) "\uD83D\uDCB4 " else "") + chapter.meta.title
                    // Timestamp is in seconds, convert to milliseconds
                    date_upload = chapter.meta.createdAt * 1000
                    // While chapters are properly sorted, authors often add promotional material as "chapters" which breaks trackers
                    // There's no way to properly filter these as they are treated the same as normal chapters
                    chapter_number = chapter.meta.number.toFloat()
                    // Can't use setUrlWithoutDomain as it uses the baseUrl instead of apiUrl
                    url = "/episodes/${chapter.id}/frames"
                },
            )
        }
        chapters.sortByDescending { chapter -> chapter.chapter_number }
        return chapters
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Overwrite to use the API instead of scraping the shared URL
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/contents/$id/episodes")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/watch/mg${chapter.url.substringBeforeLast("/").substringAfterLast("/")}"
    }

    override fun pageListParse(response: Response): List<Page> {
        // Nicomanga historically refuses to serve pages if you don't login.
        // However, due to the network attack against the site (as of July 2024) login is disabled
        // Changes may be required as the site recovers
        if (response.code == 403) {
            throw SecurityException("You need to purchase this chapter first")
        }
        if (response.code == 401) {
            throw SecurityException("Not logged in. Please login via WebView")
        }
        val r = json.parseToJsonElement(response.body.string()).jsonObject
        val frames = r["data"]!!.jsonObject["result"]!!.jsonArray
        val pages = ArrayList<Page>()
        for ((i, entry) in frames.withIndex()) {
            val frame = json.decodeFromJsonElement<Frame>(entry)
            pages.add(Page(i, frame.meta.sourceUrl, frame.meta.sourceUrl))
        }
        return pages
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Overwrite to use the API instead of scraping the shared URL
        val id = chapter.url.substringBeforeLast("/").substringAfterLast("/")
        return GET("$apiUrl/episodes/$id/frames?enable_webp=true")
    }

    override fun imageRequest(page: Page): Request {
        // Headers are required to avoid cache miss from server side
        val headers = headersBuilder()
            .set("referer", baseUrl)
            .set("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("pragma", "no-cache")
            .set("cache-control", "no-cache")
            .set("accept-encoding", "gzip, deflate, br")
            .set("user-agent", System.getProperty("http.agent")!!)
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
