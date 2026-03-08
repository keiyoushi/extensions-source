package eu.kanade.tachiyomi.extension.ar.mangapro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.internal.closeQuietly
import okio.Buffer
import rx.Observable
import tachiyomi.decoder.ImageDecoder
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

class ProChan : HttpSource() {
class ProChan : HttpSource(), ConfigurableSource {
    override val name = "ProChan"
    override val lang = "ar"
    
    // إعداد التفضيلات لقراءة الرابط المخصص
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", Context.MODE_PRIVATE)
    }

    // الرابط الأساسي يقرأ من الإعدادات أو يستخدم الافتراضي إذا لم يتم تغييره
    override val baseUrl: String by lazy {
        preferences.getString(BASE_URL_PREF, "https://prochan.net")!!.removeSuffix("/")
    }

    // استخراج الدومين تلقائياً من الرابط المدخل لاستخدامه في الكوكيز
    private val domain: String by lazy {
        baseUrl.replace("https://", "").replace("http://", "").split("/")[0]
    }

    override val supportsLatest = true
    override val versionId = 6

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::scrambledImageInterceptor)
        .addNetworkInterceptor(
            CookieInterceptor(
                domain, // يستخدم الدومين المستخرج ديناميكياً
                listOf(
                    "safe_browsing" to "off",
                    "language" to "ar",
                ),
            ),
        )
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    // إضافة خانات الإعدادات في واجهة التطبيق
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "تغيير رابط الموقع"
            summary = "الرابط الحالي: $baseUrl"
            defaultValue = "https://prochan.net"
            dialogTitle = "أدخل الرابط الجديد"
        }.let(screen::addPreference)

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 2
        }

        return fetchSearchManga(page, "", filters)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 1
        }

        return fetchSearchManga(page, "", filters)
    }

    private val pageNumber = ConcurrentHashMap<String, Int>()

    private fun searchKey(query: String, filters: FilterList): String {
        val filterPart = filters.filterIsInstance<Filter<*>>()
            .joinToString("|") { it.state.toString() }
        return "$query::$filterPart"
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val path = url.pathSegments
            if (url.host == domain && path.size >= 4 && path[0] == "series") {
                val type = path[1]
                if (type !in SUPPORTED_TYPES) {
                    throw Exception("نوع غير مدعوم")
                }
                val mangaId = path[2]
                val slug = path[3]

                val manga = SManga.create().apply {
                    this@apply.url = "/series/$type/$mangaId/$slug"
                }

                return fetchMangaDetails(manga).map {
                    MangasPage(listOf(it), false)
                }
            } else {
                throw Exception("رابط غير مدعوم")
            }
        }

        val key = searchKey(query, filters)
        if (page == 1) {
            pageNumber[key] = 1
        }

        return client.newCall(searchMangaRequest(pageNumber[key]!!, query, filters))
            .asObservableSuccess()
            .map { response ->
                val statusFilter = filters.firstInstance<StatusFilter>().selected
                val genreFilter = filters.firstInstance<GenreFilter>()
                val tagFilter = filters.firstInstance<TagFilter>()

                val data = response.parseAs<MetaData<BrowseManga>>()
                val mangas = data.data.asSequence()
                    .filter { manga ->
                        manga.type in SUPPORTED_TYPES
                    }
                    .filter { manga ->
                        statusFilter == null || manga.progress == statusFilter
                    }
                    .filter { manga ->
                        genreFilter.included.isEmpty() ||
                            manga.metadata.genres.containsAll(genreFilter.included)
                    }
                    .filter { manga ->
                        genreFilter.excluded.none { it in manga.metadata.genres }
                    }
                    .filter { manga ->
                        tagFilter.included.isEmpty() ||
                            manga.metadata.tags.containsAll(tagFilter.included)
                    }
                    .filter { manga ->
                        tagFilter.excluded.none { it in manga.metadata.tags }
                    }
                    .map { manga ->
                        SManga.create().apply {
                            url = "/series/${manga.type}/${manga.id}/${manga.slug}"
                            title = manga.title
                            thumbnail_url = (manga.coverImageApp?.desktop ?: manga.coverImage)?.let {
                                if (it.startsWith("/")) {
                                    manga.cdn?.let { cdn ->
                                        "https://$cdn.$domain$it"
                                    }
                                } else {
                                    it
                                }
                            }
                        }
                    }
                    .toList()

                MangasPage(mangas, data.meta.hasNextPage())
            }
            .flatMap {
                if (it.mangas.isEmpty() && it.hasNextPage) {
                    pageNumber[key] = pageNumber[key]!! + 1
                    fetchSearchManga(pageNumber[key]!!, query, filters)
                } else {
                    if (!it.hasNextPage) pageNumber.remove(key)
                    Observable.just(it)
                }
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/public/series/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", "approved")
            addQueryParameter("limit", "18")
            addQueryParameter("page", page.toString())
            query.takeIf(String::isNotBlank)?.also { query ->
                addQueryParameter("search", query)
            }
            filters.firstInstance<TypeFilter>().selected?.also { type ->
                addQueryParameter("type", type)
            }
            addQueryParameter("sort", filters.firstInstance<SortFilter>().selected)
            filters.firstInstance<YearFilter>().selected?.also { year ->
                addQueryParameter("year", year)
            }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        TypeFilter(),
        SortFilter(),
        YearFilter(),
        StatusFilter(),
        GenreFilter(),
        TagFilter(),
    )

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.extractNextJs<Series>()!!.series

        return SManga.create().apply {
            url = "/series/${manga.type}/${manga.id}/${manga.slug}"
            title = manga.title
            artist = manga.metadata.artist.joinToString()
            author = manga.metadata.author.joinToString()
            description = buildString {
                manga.description?.also { append(it.trim(), "\n\n") }
                buildList {
                    addAll(manga.metadata.altTitles)
                    manga.metadata.originalTitle?.also { add(it) }
                }.also {
                    if (it.isNotEmpty()) {
                        append("عناوين بديلة\n")
                        it.forEach { title ->
                            append("- ", title, "\n")
                        }
                        append("\n")
                    }
                }
            }.trim()
            genre = buildList {
                add(manga.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                manga.metadata.year?.also { add(it) }
                manga.metadata.origin?.also { origin ->
                    add(origin.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                }
                when (manga.type) {
                    "manga" -> add("مانجا")
                    "manhwa" -> add("مانها")
                    "manhua" -> add("مانهوا")
                }
                if (manga.metadata.genres.isNotEmpty()) {
                    val genreMap = genres.associate { it.second to it.first }
                    manga.metadata.genres.mapTo(this) { genreMap[it] ?: it }
                }
                if (manga.metadata.tags.isNotEmpty()) {
                    val tagsMap = tags.associate { it.second to it.first }
                    manga.metadata.tags.mapTo(this) { tagsMap[it] ?: it }
                }
            }.joinToString()
            status = when (manga.progress?.trim()) {
                "مستمر" -> SManga.ONGOING
                "مكتمل" -> SManga.COMPLETED
                "متوقف" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = (manga.coverImageApp?.desktop ?: manga.metadata.coverImage)?.let {
                if (it.startsWith("/")) {
                    manga.cdn?.let { cdn ->
                        "https://$cdn.$domain$it"
                    }
                } else {
                    it
                }
            }
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<InitialChapters>()!!
        val chapters = data.initialChapters.toMutableList()
        val size = chapters.size
        var page = 2
        val type = response.request.url.pathSegments[1]
        val id = response.request.url.pathSegments[2]
        val slug = response.request.url.pathSegments[3]

        while (data.totalChapters > chapters.size) {
            val request = GET("$baseUrl/api/public/$type/$id/chapters?page=${page++}&limit=$size&order=desc", headers)
            val nextChapters = client.newCall(request).execute()
                .also {
                    if (!it.isSuccessful) {
                        it.close()
                        throw Exception("HTTP ${it.code}")
                    }
                }
                .parseAs<Data<List<Chapter>>>()

            chapters.addAll(nextChapters.data)
        }

        countViews(id)

        return chapters
            .filter { it.language == "AR" }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/series/$type/$id/$slug/${chapter.id}/${chapter.number}"
                    name = buildString {
                        append("\u200F") // rtl marker

                        if (chapter.coins != null && chapter.coins > 0) {
                            append("🔒 ")
                        }

                        append("الفصل ")
                        append(
                            chapter.number.toFloat().toString().substringBefore(".0"),
                        )

                        chapter.title?.trim()?.takeIf { it.isNotBlank() }?.let { trimmedTitle ->
                            if (trimmedTitle != chapter.number.trim() && trimmedTitle != chapter.number) {
                                append(" \u200F- ")
                                append(trimmedTitle)
                            }
                        }
                    }
                    scanlator = chapter.uploader ?: "\u200B"
                    chapter_number = chapter.number.toFloat()
                    date_upload = dateFormat.tryParse(chapter.createdAt)
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun getChapterUrl(chapter: SChapter): String {
        val url = if (chapter.url.startsWith("{")) {
            chapter.url.parseAs<ChapterUrl>()
        } else {
            chapter.url
        }

        return "$baseUrl$url"
    }

    override fun pageListParse(response: Response): List<Page> {
        val responseBody = response.body.string()
        val imageData = responseBody
            .extractNextJsRsc<Images>()
        if (imageData == null) {
            val coins = responseBody.extractNextJsRsc<Coins>()?.coins
            if (coins != null && coins > 0) {
                throw Exception("فصل مدفوع")
            } else {
                return emptyList()
            }
        }

        val seriesId = response.request.url.pathSegments[2]
        val chapterId = response.request.url.pathSegments[4]

        val images = imageData.images.toMutableList()
        val maps = mutableListOf<ScrambledData>()

        if (imageData.deferredMedia != null) {
            val deferredUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("chapter-deferred-media")
                .addPathSegment(chapterId)
                .addQueryParameter("token", imageData.deferredMedia.token)
                .build()

            val deferredImages = client.newCall(GET(deferredUrl, headers))
                .execute().parseAs<Data<DeferredImages>>()

            images.addAll(deferredImages.data.images)
            maps.addAll(deferredImages.data.maps)
        }

        countViews(seriesId, chapterId)

        val chapterUrl = response.request.url.toString()
        val pages = mutableListOf<Page>()

        images.mapIndexedTo(pages) { index, imageUrl ->
            Page(index, chapterUrl, imageUrl)
        }
        maps.mapIndexedTo(pages) { index, scrambledData ->
            Page(pages.size + index, chapterUrl, "http://$SCRAMBLED_IMAGE_HOST/#${scrambledData.toJsonString()}")
        }

        return pages
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, headers)
    }

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != SCRAMBLED_IMAGE_HOST) {
            return chain.proceed(request)
        }

        val chapterUrl = request.header("Referer")!!
        val cdn = when (chapterUrl.toHttpUrl().pathSegments[1]) {
            "manga" -> "cdn1"
            "manhua" -> "cdn2"
            else -> "cdn3"
        }

        val scrambledImage = when (val scrambledData = url.fragment!!.parseAs<ScrambledData>()) {
            is ScrambledImage -> scrambledData
            is ScrambledImageToken -> decodeScrambledImageToken(scrambledData)
        }

        val (puzzleMode, layout) = scrambledImage.mode.split("_", limit = 2)

        require(scrambledImage.dim.size >= 2) { "Invalid dim: ${scrambledImage.dim}" }

        val width = scrambledImage.dim[0]
        val height = scrambledImage.dim[1]

        val orderedPieces = scrambledImage.order.map { scrambledImage.pieces[it] }
        val pieceBitmaps = runBlocking {
            orderedPieces.map { pieceUrl ->
                async(Dispatchers.IO.limitedParallelism(2)) {
                    var imgUrl = if (pieceUrl.startsWith("/")) {
                        "https://$cdn.$domain$pieceUrl"
                    } else {
                        pieceUrl
                    }.toHttpUrl()
                    if (imgUrl.host.startsWith("cdn")) {
                        val payload = Url(url = imgUrl.toString()).toJsonString().toRequestBody(JSON_MEDIA_TYPE)
                        val signHeaders = headersBuilder()
                            .set("Sec-Fetch-Site", "same-origin")
                            .set("Referer", chapterUrl)
                            .build()
                        val signRequest = POST("$baseUrl/api/cdn-image/sign", signHeaders, payload)
                        val response = client.newCall(signRequest).await()
                        if (response.isSuccessful) {
                            val token = response.parseAs<Token>()
                            imgUrl = imgUrl.newBuilder()
                                .setQueryParameter("token", token.token)
                                .setQueryParameter("expires", token.expires.toString())
                                .build()
                        } else {
                            response.close()
                        }
                    }
                    val pieceRequest = request.newBuilder().url(imgUrl).build()
                    val response = client.newCall(pieceRequest).await()
                    response.body.use { body ->
                        // use Tachiyomi ImageDecoder because android.graphics.BitmapFactory doesn't handle avif
                        val decoder = ImageDecoder.newInstance(body.byteStream())
                            ?: throw Exception("Failed to create decoder")
                        try {
                            decoder.decode() ?: throw Exception("Failed to decode piece")
                        } finally {
                            decoder.recycle()
                        }
                    }
                }
            }.awaitAll()
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        try {
            when (puzzleMode) {
                "vertical" -> {
                    var x = 0f
                    for (bitmap in pieceBitmaps) {
                        canvas.drawBitmap(bitmap, x, 0f, null)
                        x += bitmap.width
                    }
                }
                "grid" -> {
                    val (cols, rows) = layout.split('x', limit = 2).map { it.toInt() }
                    var y = 0f
                    for (r in 0 until rows) {
                        var x = 0f
                        var maxHeightInRow = 0f
                        for (c in 0 until cols) {
                            val index = r * cols + c
                            if (index < pieceBitmaps.size) {
                                val bitmap = pieceBitmaps[index]
                                canvas.drawBitmap(bitmap, x, y, null)
                                x += bitmap.width
                                maxHeightInRow = maxOf(maxHeightInRow, bitmap.height.toFloat())
                            }
                        }
                        y += maxHeightInRow
                    }
                }
                else -> throw IOException("Unknown puzzle mode: $puzzleMode")
            }

            val buffer = Buffer().apply {
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream())
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(buffer.asResponseBody("image/jpg".toMediaType(), buffer.size))
                .build()
        } finally {
            pieceBitmaps.forEach { it.recycle() }
            resultBitmap.recycle()
        }
    }

    private val sessionKey = ConcurrentHashMap<Int, Pair<String, Long>>()
    private val sessionKeyLock = Any()

    private fun decodeScrambledImageToken(data: ScrambledImageToken): ScrambledImage {
        val value = String(urlSafeBase64(data.token), Charsets.UTF_8)
            .parseAs<ScrambledImageTokenValue>()

        val iv = urlSafeBase64(value.iv)
        val tag = urlSafeBase64(value.tag)
        val encryptedData = urlSafeBase64(value.data)

        val key = when (value.m) {
            "browser" if value.v == 2 -> {
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(
                        "prochan-browser-map:2e6f9a1c4d8b7e3f0a5c9d2b6e1f4a8c7d3b0e6a9f2c5d8b1e4a7c0d3f6b9e2:${value.cid}"
                            .toByteArray(Charsets.UTF_8),
                    )
                SecretKeySpec(hash, "AES")
            }
            // Untested, couldn't find a chapter which uses this, possibly for paid chapters?
            "browser_session" if value.v == 3 -> synchronized(sessionKeyLock) {
                val time = System.currentTimeMillis()
                val key = sessionKey[value.cid]?.takeIf { it.second > time }?.first ?: run {
                    val request = GET("$baseUrl/chapter-map-session-key/${value.cid}", headers)
                    val response = client.newCall(request).execute().parseAs<Data<Key>>()

                    sessionKey[value.cid] = response.data.key to (time + 120000)

                    response.data.key
                }

                SecretKeySpec(urlSafeBase64(key), "AES")
            }
            else -> throw Exception("Unknown method")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            val spec = GCMParameterSpec(128, iv)

            init(Cipher.DECRYPT_MODE, key, spec)
        }

        val decryptedBytes = cipher.doFinal(encryptedData + tag)
        return String(decryptedBytes, Charsets.UTF_8).parseAs()
    }

    private fun urlSafeBase64(data: String) = Base64.UrlSafe
        .withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
        .decode(data)

    private fun countViews(seriesId: String, chapterId: String? = null) {
        val userAgent = headers["User-Agent"]!!
        val payload = ViewsDto(
            chapterId = chapterId?.toInt(),
            contentId = seriesId.toInt(),
            deviceType = when {
                MOBILE_REGEX.containsMatchIn(userAgent) -> "mobile"
                TABLES_REGEX.containsMatchIn(userAgent) -> "tablet"
                else -> "desktop"
            },
            surface = when {
                chapterId == null -> "series"
                else -> "chapter"
            },
        ).toJsonString().toRequestBody(JSON_MEDIA_TYPE)

        client.newCall(POST("$baseUrl/api/views", headers, payload))
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            Log.e(name, "Failed to count views, HTTP ${response.code}")
                        }
                        response.closeQuietly()
                    }
                    override fun onFailure(call: Call, e: okio.IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private val SUPPORTED_TYPES = setOf("manga", "manhwa", "manhua")
private const val SCRAMBLED_IMAGE_HOST = "127.0.0.1"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val MOBILE_REGEX = Regex("mobile|android|iphone|ipad|ipod", RegexOption.IGNORE_CASE)
private val TABLES_REGEX = Regex("tablet", RegexOption.IGNORE_CASE)
