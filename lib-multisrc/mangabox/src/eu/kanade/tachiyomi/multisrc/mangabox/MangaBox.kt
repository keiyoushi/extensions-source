package eu.kanade.tachiyomi.multisrc.mangabox

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangabox.imagesize.ImageSize
import eu.kanade.tachiyomi.multisrc.mangabox.imagesize.WebpSizeGetter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch

abstract class MangaBox :
    HttpSource(),
    ConfigurableSource {

    protected open val dateFormat: SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        Locale.ROOT, // Changed to Locale.ROOT as per CONTRIBUTING.md rules
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::mergeImagesInterceptor)
        .addInterceptor(::useAltCdnInterceptor)
        .build()

    private fun SharedPreferences.getMergeImagesPref(): Boolean = getBoolean(PREF_MERGE_IMAGES, false)

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var mergeImages: Boolean? = null
        get() {
            if (field != null) {
                return field
            }

            field = preferences.getMergeImagesPref()
            return field
        }

    private val apiChapterListUrl: String
        get() = "$baseUrl/api/manga/__SLUG__/chapters"

    private val apiChapterPageUrl: String
        get() = "$baseUrl/manga/__MANGA__/__CHAPTER__"

    private val cdnSet =
        MangaBoxLinkedCdnSet() // Stores all unique CDNs that the extension can use to retrieve chapter images

    private class MangaBoxFallBackTag // Custom empty class tag to use as an identifier that the specific request is fallback-able

    private fun HttpUrl.getBaseUrl(): String = "${URL_PREFIX}${this.host}${
        when (this.port) {
            80, 443 -> ""
            else -> ":${this.port}"
        }
    }"

    private fun mergeImagesInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.toString().startsWith("https://127.0.0.1/merge?")) {
            val w = url.queryParameter("w")!!.toInt()
            val h = url.queryParameter("h")!!.toInt()

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

            try {
                val canvas = Canvas(result)

                val length = url.queryParameter("length")!!.toInt()

                var yOffset = 0

                for (i in 0..<length) {
                    val imageUrl = url.queryParameter(i.toString())!!
                    val bitmap = BitmapFactory.decodeStream(
                        chain
                            .proceed(request.newBuilder().url(imageUrl).build())
                            .body
                            .byteStream(),
                    )
                    canvas.drawBitmap(bitmap, 0f, yOffset.toFloat(), null)
                    yOffset += bitmap.height
                    bitmap.recycle()
                }

                return Response.Builder().body(
                    Buffer()
                        .also {
                            result.compress(Bitmap.CompressFormat.WEBP, 100, it.outputStream())
                        }
                        .asResponseBody("image/webp".toMediaType()),
                )
                    .request(request)
                    .protocol(Protocol.HTTP_1_0)
                    .code(200)
                    .message("")
                    .build()
            } finally {
                result.recycle()
            }
        } else {
            return chain.proceed(request)
        }
    }

    private fun useAltCdnInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (cdnSet.isEmpty()) {
            return chain.proceed(request)
        }
        val requestTag = request.tag(MangaBoxFallBackTag::class.java)
        val originalResponse: Response? = try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (requestTag == null) {
                throw e
            } else {
                null
            }
        }

        if (requestTag == null || originalResponse?.isSuccessful == true) {
            requestTag?.let {
                // Move working cdn to first so it gets priority during iteration
                cdnSet.moveItemToFirst(request.url.getBaseUrl())
            }

            return originalResponse!!
        }

        // Close the original response if it's not successful
        originalResponse?.close()

        for (cdnUrl in cdnSet) {
            var tryResponse: Response? = null

            try {
                val newUrl = cdnUrl.toHttpUrl().newBuilder()
                    .encodedPath(request.url.encodedPath)
                    .fragment(request.url.fragment)
                    .build()

                // Create a new request with the updated URL
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                // Proceed with the new request
                tryResponse = chain.proceed(newRequest)

                // Check if the response is successful
                if (tryResponse.isSuccessful) {
                    // Move working cdn to first so it gets priority during iteration
                    cdnSet.moveItemToFirst(newRequest.url.getBaseUrl())

                    return tryResponse
                }

                tryResponse.close()
            } catch (_: IOException) {
                tryResponse?.close()
            }
        }

        // If all CDNs fail, throw an error
        throw IOException("All CDN attempts failed.")
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    open val popularUrlPath = "manga-list/hot-manga?page="

    open val latestUrlPath = "manga-list/latest-manga?page="

    open val simpleQueryPath = "search/story/"

    // ============================== Popular ==============================

    open fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap, div.comic-list > .list-comic-item-wrap"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$popularUrlPath$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            if (selector.isEmpty()) false else document.select(selector).first() != null
        }
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaFromElement(element: Element): SManga = mangaFromElement(element)

    open fun popularMangaNextPageSelector() = "div.group_page, div.group-page a:not([href]) + a:not(:contains(Last))"

    // ============================== Latest ===============================

    open fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$latestUrlPath$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            if (selector.isEmpty()) false else document.select(selector).first() != null
        }
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesFromElement(element: Element): SManga = mangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        val url = "$baseUrl/$simpleQueryPath".toHttpUrl().newBuilder()
            .addPathSegment(normalizeSearchQuery(query))
            .addQueryParameter("page", page.toString())
            .build()

        GET(url, headers)
    } else {
        val url = "$baseUrl/genre".toHttpUrl().newBuilder()
        var sort: String? = null
        var status: String? = null

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                else -> {}
            }
        }

        val id = if (sort != null && status != null) {
            FILTER_ID_MAP[Pair(sort, status)]
        } else {
            null
        }

        id?.let { url.addQueryParameter("filter", it) }
        url.addQueryParameter("page", page.toString())

        GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            if (selector.isEmpty()) false else document.select(selector).first() != null
        }
        return MangasPage(mangas, hasNextPage)
    }

    open fun searchMangaSelector() = ".panel_story_list .story_item, div.list-truyen-item-wrap, div.list-comic-item-wrap"

    open fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    open fun searchMangaNextPageSelector() = "a.page_select + a:not(.page_last), a.page-select + a:not(.page-last)"

    private fun mangaFromElement(element: Element, urlSelector: String = "h3 a"): SManga = SManga.create().apply {
        val urlElement = element.selectFirst(urlSelector)!!
        url = urlElement.attr("abs:href")
            .substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
        title = urlElement.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    // ============================== Details ==============================

    open val mangaDetailsMainSelector = "div.manga-info-top, div.panel-story-info"

    open val thumbnailSelector = "div.manga-info-pic img, span.info-image img"

    open val descriptionSelector = "div#noidungm, div#panel-story-info-description, div#contentBox"

    open val altNameSelector = ".story-alternative, tr:has(.info-alternative) h2"

    open val altName = "Alternative Name: "

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    private fun checkForRedirectMessage(document: Document) {
        if (document.select("body").text().startsWith("REDIRECT :")) {
            throw Exception("Source URL has changed")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(mangaDetailsMainSelector)
        if (infoElement != null) {
            title = infoElement.selectFirst("h1, h2")!!.text()
            author = infoElement.select("li:contains(author) a, td:containsOwn(author) + td a")
                .eachText().joinToString()
            status = parseStatus(
                infoElement.select("li:contains(status), td:containsOwn(status) + td").text(),
            )
            genre = infoElement.selectFirst("div.manga-info-top li:contains(genres)")
                ?.select("a")?.joinToString { it.text() } // kakalot
                ?: infoElement.select("td:containsOwn(genres) + td a")
                    .joinToString { it.text() } // nelo
        } else {
            checkForRedirectMessage(document)
        }

        description = document.selectFirst(descriptionSelector)?.ownText()
            ?.replace("""^$title summary:\s""".toRegex(), "")
            ?.replace("""<\s*br\s*/?>""".toRegex(), "\n")
            ?.replace("<[^>]*>".toRegex(), "")
        thumbnail_url = document.selectFirst(thumbnailSelector)!!.attr("abs:src")

        // add alternative name to manga description
        val altNameElement = document.selectFirst(altNameSelector)
        if (altNameElement != null) {
            val altNameText = altNameElement.ownText()
            if (altNameText.isNotEmpty()) {
                description = when {
                    description.isNullOrEmpty() -> altName + altNameText
                    else -> description + "\n\n$altName" + altNameText
                }
            }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.split("/").last()
        return GET("${apiChapterListUrl.replace("__SLUG__", slug)}?limit=$CHAPTER_LIST_TAKE&offset=0", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResult = response.parseAs<ApiResponse>()

        val slug = response.request.url.pathSegments.let { it[it.size - 2] }

        val rawChaptersList = mutableListOf<ApiChapter>()

        rawChaptersList.addAll(apiResult.data.chapters)

        // Iterate if chapter contains more than the initial take
        if (apiResult.data.pagination.hasMore) {
            var offsetMultiple = 1
            val baseChapterListUrl = apiChapterListUrl.replace("__SLUG__", slug)

            while (true) {
                val nextPageResponse =
                    client.newCall(
                        GET(
                            "$baseChapterListUrl?limit=$CHAPTER_LIST_TAKE&offset=${CHAPTER_LIST_TAKE * offsetMultiple}",
                            headers,
                        ),
                    ).execute().parseAs<ApiResponse>()

                rawChaptersList.addAll(nextPageResponse.data.chapters)

                if (nextPageResponse.data.pagination.hasMore) {
                    offsetMultiple += 1
                } else {
                    break
                }
            }
        }

        return rawChaptersList.map { apiChapter ->
            SChapter.create().apply {
                name = apiChapter.chapterName
                url = apiChapterPageUrl.replace("__MANGA__", slug).replace("__CHAPTER__", apiChapter.chapterSlug)
                chapter_number = apiChapter.chapterNum
                scanlator = baseUrl.replace("https://", "")
                date_upload = dateFormat.tryParse(apiChapter.updatedAt)
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    private fun extractArray(scriptContent: String, regex: Regex): List<String> {
        val match = regex.find(scriptContent)
        return match?.groupValues?.get(1)?.split(",")?.map {
            it.trim().removeSurrounding("\"").replace("\\/", "/").removeSuffix("/")
        } ?: emptyList()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val content = document.select("script:containsData(cdns =)").joinToString("\n") { it.data() }
        val cdns = extractArray(content, cdnsRegex) + extractArray(content, backupImageRegex)
        val chapterImages = extractArray(content, chapterImagesRegex)

        // Add all parsed cdns to set
        cdnSet.addAll(cdns)

        val (numImages, imageUrls) = if (chapterImages.isNotEmpty()) {
            val httpUrl = cdns[0].toHttpUrl()
            Pair(
                chapterImages.size,
                chapterImages.asSequence().map { imagePath ->
                    httpUrl
                        .newBuilder()
                        .encodedPath("/$imagePath".replace("//", "/")) // replace ensures that there's at least one trailing slash prefix
                        .build()
                        .toString()
                },
            )
        } else {
            val elements = document.select("div.container-chapter-reader > img")
            Pair(
                elements.size,
                elements.asSequence().map { img ->
                    img.absUrl("src")
                },
            )
        }

        return if (mergeImages == true) {
            val latch = CountDownLatch(numImages)
            val sizes = MutableList<ImageSize?>(numImages) { null }
            val headers = headersBuilder().set("Range", WebpSizeGetter.RANGE).build()

            imageUrls.forEachIndexed { i, url ->
                client.newCall(
                    GET(url, headers).newBuilder()
                        .tag(MangaBoxFallBackTag::class.java, MangaBoxFallBackTag()).build(),
                ).enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            latch.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            sizes[i] = WebpSizeGetter(response.body.byteStream()).get()
                            latch.countDown()
                        }
                    },
                )
            }

            latch.await()

            val imageList = mutableListOf<MergeImage>()

            for ((url, size) in imageUrls.zip(sizes.asSequence())) {
                val prev = imageList.lastOrNull()
                val prevSize = prev?.size
                if (
                    // size is known
                    size != null &&

                    // previous size is known
                    prevSize != null &&

                    // widths are equal
                    size.w == prevSize.w &&

                    // merged image is not too long
                    3 * prevSize.w > 2 * prevSize.h + size.h
                ) {
                    prev.urls.add(url)
                    prevSize.h += size.h
                } else {
                    imageList.add(MergeImage(mutableListOf(url), size))
                }
            }

            imageList.mapIndexed { i, image ->
                Page(
                    i,
                    url = document.location(),
                    imageUrl = image.toString(),
                )
            }
        } else {
            imageUrls.mapIndexed { i, url ->
                Page(
                    i,
                    url = document.location(),
                    imageUrl = url,
                )
            }.toList()
        }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers).newBuilder()
        .tag(MangaBoxFallBackTag::class.java, MangaBoxFallBackTag()).build()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    // Based on change_alias JS function from Mangakakalot's website
    @SuppressLint("DefaultLocale")
    open fun normalizeSearchQuery(query: String): String {
        var str = query.lowercase()
        str = str.replace("[àáạảãâầấậẩẫăằắặẳẵ]".toRegex(), "a")
        str = str.replace("[èéẹẻẽêềếệểễ]".toRegex(), "e")
        str = str.replace("[ìíịỉĩ]".toRegex(), "i")
        str = str.replace("[òóọỏõôồốộổỗơờớợởỡ]".toRegex(), "o")
        str = str.replace("[ùúụủũưừứựửữ]".toRegex(), "u")
        str = str.replace("[ỳýỵỷỹ]".toRegex(), "y")
        str = str.replace("đ".toRegex(), "d")
        str = str.replace(
            """!|@|%|\^|\*|\(|\)|\+|=|<|>|\?|/|,|\.|:|;|'| |"|&|#|\[|]|~|-|$|_""".toRegex(),
            "_",
        )
        str = str.replace("_+_".toRegex(), "_")
        str = str.replace("""^_+|_+$""".toRegex(), "")
        return str
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(getSortFilters()),
        StatusFilter(getStatusFilters()),
        GenreFilter(getGenreFilters()),
    )

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = PREF_MERGE_IMAGES
            title = "Merge Split Images"
            summary = "Images are sometimes split vertically. " +
                "This setting enables detecting and merging split images. " +
                "Note that this isn't 100% accurate."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                // Update values
                mergeImages = newValue as Boolean
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_MERGE_IMAGES = "pref_merge_images"
        private const val CHAPTER_LIST_TAKE = 1000
        private const val URL_PREFIX = "https://"

        private val cdnsRegex = Regex("""cdns\s*=\s*\[([^]]+)]""")
        private val backupImageRegex = Regex("""backupImage\s*=\s*\[([^]]+)]""")
        private val chapterImagesRegex = Regex("""chapterImages\s*=\s*\[([^]]+)]""")

        private val FILTER_ID_MAP = mapOf(
            Pair("newest", "all") to "1",
            Pair("newest", "completed") to "2",
            Pair("newest", "ongoing") to "3",
            Pair("latest", "all") to "4",
            Pair("latest", "completed") to "5",
            Pair("latest", "ongoing") to "6",
            Pair("topview", "all") to "7",
            Pair("topview", "completed") to "8",
            Pair("topview", "ongoing") to "9",
        )
    }
}

private class MergeImage(
    val urls: MutableList<String>,
    val size: ImageSize?,
) {
    override fun toString(): String {
        if (urls.size == 1) {
            return urls[0]
        }

        val (w, h) = size!!

        val builder = HTTP_URL
            .newBuilder()
            .addQueryParameter("w", w.toString())
            .addQueryParameter("h", h.toString())
            .addQueryParameter("length", urls.size.toString())

        urls.forEachIndexed { i, url ->
            builder.addQueryParameter(i.toString(), url)
        }

        return builder.build().toString()
    }

    companion object {
        private val HTTP_URL = "https://127.0.0.1/merge".toHttpUrl()
    }
}
