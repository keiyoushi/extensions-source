package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import kotlin.experimental.xor

class NicovideoSeiga : HttpSource() {
    // Nicovideo Seiga contains illustrations, manga and books from Bookwalker. This extension will focus on manga only.
    override val baseUrl: String = "https://seiga.nicovideo.jp"
    override val lang: String = "ja"
    override val name: String = "Nicovideo Seiga"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()
    private val application: Application by injectLazy()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val currentPage = response.request.url.queryParameter("page")!!.toInt()
        val doc = Jsoup.parse(response.body.string())
        val mangaCount = doc.select("#main_title > h2 > span").text().trim().dropLast(1).toInt()
        val mangaPerPage = 20
        val mangaList = doc.select("#comic_list > ul > li")
        val mangas = ArrayList<SManga>()
        for (manga in mangaList) {
            val mangaElement = manga.select("div > .description > div > div")
            mangas.add(
                SManga.create().apply {
                    setUrlWithoutDomain(
                        baseUrl + mangaElement.select(".comic_icon > div > a").attr("href"),
                    )
                    title = mangaElement.select(".mg_body > .title > a").text()
                    // While the site does label who are the author and artists are, there is no formatting standard at all!
                    // It becomes impossible to parse the names and their specific roles
                    // So we are not going to process this at all
                    author = mangaElement.select(".mg_description_header > .mg_author > a").text()
                    // Nicovideo doesn't provide large thumbnails in their searches and manga listings unfortunately
                    // A larger thumbnail is only available after going into the details page
                    thumbnail_url = mangaElement.select(".comic_icon > div > a > img").attr("src")
                    val statusText =
                        mangaElement.select(".mg_description_header > .mg_icon > .content_status > span")
                            .text()
                    status = when (statusText) {
                        "連載" -> {
                            SManga.ONGOING
                        }
                        "完結" -> {
                            SManga.COMPLETED
                        }
                        else -> {
                            SManga.UNKNOWN
                        }
                    }
                },
            )
        }
        return MangasPage(mangas, mangaCount - mangaPerPage * currentPage > 0)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga/list?page=$page&sort=manga_updated")

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga/list?page=$page&sort=manga_view")

    override fun searchMangaParse(response: Response): MangasPage {
        val currentPage = response.request.url.queryParameter("page")!!.toInt()
        val doc = Jsoup.parse(response.body.string())
        val mangaCount =
            doc.select("#mg_wrapper > div > div.header > div.header__result-summary").text().trim()
                .split("：")[1].toInt()
        val mangaPerPage = 20
        val mangaList = doc.select(".search_result__item")
        val mangas = ArrayList<SManga>()
        for (manga in mangaList) {
            mangas.add(
                SManga.create().apply {
                    setUrlWithoutDomain(
                        baseUrl + manga.select(".search_result__item__thumbnail > a")
                            .attr("href"),
                    )
                    title =
                        manga.select(".search_result__item__info > .search_result__item__info--title > a")
                            .text().trim()
                    // While the site does label who the author and artists are, there is no formatting standard at all!
                    // It becomes impossible to parse the names and their specific roles
                    // So we are not going to process this at all
                    author =
                        manga.select(".search_result__item__info > .search_result__item__info--author")
                            .text()
                    // Nicovideo doesn't provide large thumbnails in their searches and manga listings unfortunately
                    // A larger thumbnail/cover art is only available after going into the chapter listings
                    thumbnail_url = manga.select(".search_result__item__thumbnail > a > img")
                        .attr("data-original")
                },
            )
        }
        return MangasPage(mangas, mangaCount - mangaPerPage * currentPage > 0)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/manga/search/?q=$query&page=$page&sort=score")

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val doc = Jsoup.parse(response.body.string())
        // The description is a mix of synopsis and news announcements
        // This is just how mangakas use this site
        description =
            doc.select("#contents > div.mg_work_detail > div > div.row > div.description_text")
                .text()
        // A better larger cover art is available here
        thumbnail_url =
            doc.select("#contents > div.primaries > div.main_visual > a > img").attr("src")
        val statusText =
            doc.select("#contents > div.mg_work_detail > div > div:nth-child(2) > div.tip.content_status.status_series > span")
                .text()
        status = when (statusText) {
            "連載" -> {
                SManga.ONGOING
            }
            "完結" -> {
                SManga.COMPLETED
            }
            else -> {
                SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapters = ArrayList<SChapter>()
        val chapterList = doc.select("#episode_list > ul > li")
        val mangaId = response.request.url.toUrl().toString().substringAfterLast('/').substringBefore('?')
        val sharedPref = application.getSharedPreferences("source_${id}_time_found:$mangaId", 0)
        val editor = sharedPref.edit()
        // After logging in, any chapters bought should show up as well
        // Users will need to refresh their chapter list after logging in
        for (chapter in chapterList) {
            chapters.add(
                SChapter.create().apply {
                    // Unfortunately we cannot filter out promotional materials in the chapter list,
                    // nor we can determine the chapter number from the title
                    // That would require understanding the context of the title (See One Punch Man and Uzaki-chan for example)
                    // Unless we have a machine learning algorithm in place, it's simply not possible
                    name = chapter.select("div > div.description > div.title > a").text()
                    setUrlWithoutDomain(
                        baseUrl + chapter.select("div > div.description > div.title > a")
                            .attr("href"),
                    )
                    // The data-number attribute is the only way we can determine chapter orders,
                    // without that this extension would have been impossible to make
                    // Note: Promotional materials also count as "chapters" here, so auto tracking unfortunately does not work at all
                    chapter_number = chapter.select("div").attr("data-number").toFloat()
                    // We can't determine the upload date from the website
                    // Store date_upload when a chapter is found for the first time
                    val dateFound = System.currentTimeMillis()
                    if (!sharedPref.contains(chapter_number.toString())) {
                        editor.putLong(chapter_number.toString(), dateFound)
                    }
                    date_upload = sharedPref.getLong(chapter_number.toString(), dateFound)
                },
            )
        }
        editor.apply()
        chapters.sortByDescending { chapter -> chapter.chapter_number }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val pages = ArrayList<Page>()
        // Nicovideo will refuse to serve any pages if the user has not logged in
        if (!doc.select("#login_manga").isEmpty()) {
            throw SecurityException("Not logged in. Please login via WebView first")
        }
        val pageList = doc.select("#page_contents > li")
        for (page in pageList) {
            val pageNumber = page.attr("data-page-index").toInt()
            val url = page.select("div > img").attr("data-original")
            pages.add(Page(pageNumber, url, url))
        }
        return pages
    }

    override fun imageRequest(page: Page): Request {
        // Headers are required to avoid cache miss from server side
        val headers = headersBuilder()
            .set("referer", "https://seiga.nicovideo.jp/")
            .set("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("pragma", "no-cache")
            .set("cache-control", "no-cache")
            .set("accept-encoding", "gzip, deflate, br")
            .set(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36",
            )
            .set("sec-fetch-dest", "image")
            .set("sec-fetch-mode", "no-cors")
            .set("sec-fetch-site", "cross-site")
            .set("sec-gpc", "1")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

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
        val body = decryptedImage.toResponseBody("image/${getImageType(decryptedImage)}".toMediaTypeOrNull())
        return response.newBuilder().body(body).build()
    }

    /**
     * Paid images are xor encrypted in Nicovideo.
     * The image url is displayed in the document in noscript environment
     * It will look like the following:
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
