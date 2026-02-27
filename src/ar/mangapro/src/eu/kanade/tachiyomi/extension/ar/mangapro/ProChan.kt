package eu.kanade.tachiyomi.extension.ar.mangapro

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
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
import okhttp3.Headers
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
import okio.buffer
import okio.source
import rx.Observable
import tachiyomi.decoder.ImageDecoder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

class ProChan : HttpSource() {
    override val name = "ProChan"
    override val lang = "ar"
    private val domain = "prochan.net"
    override val baseUrl = "https://$domain"
    override val supportsLatest = true
    override val versionId = 5

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::zipFileInterceptor)
        .addInterceptor(::scrambledImageInterceptor)
        .addNetworkInterceptor(
            CookieInterceptor(
                domain,
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

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val statusFilter = filters.firstInstance<StatusFilter>().selected
                val genreFilter = filters.firstInstance<GenreFilter>()
                val tagFilter = filters.firstInstance<TagFilter>()

                val mangas = response.parseAs<Data<List<BrowseManga>>>().data.asSequence()
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

                MangasPage(mangas, false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/public/series/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("status", "approved")
            addQueryParameter("limit", "9999")
            addQueryParameter("page", "1")
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

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val type = mangaUrl.pathSegments[1]
        val id = mangaUrl.pathSegments[2]
        val slug = mangaUrl.pathSegments[3]

        return GET("$baseUrl/api/public/$type/$id/chapters?page=1&limit=9999&order=desc#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Data<List<Chapter>>>()
        val type = response.request.url.pathSegments[2]
        val id = response.request.url.pathSegments[3]
        val slug = response.request.url.fragment!!

        countViews(id)

        return data.data
            .filter { it.language == "AR" }
            .map { chapter ->
                SChapter.create().apply {
                    url = ChapterUrl(
                        url = "/series/$type/$id/$slug/${chapter.id}/${chapter.number}",
                        driveFileId = chapter.metadata.driveFileId,
                    ).toJsonString()
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
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val isDownloader = Exception().stackTrace.any {
            it.className.contains("downloader", ignoreCase = true)
        }

        val driveFileId = chapter.url.parseAs<ChapterUrl>().driveFileId
        val canUseZip = driveFileId != null

        return if (isDownloader && canUseZip) {
            Observable.fromCallable { zipPageList(driveFileId) }
                .doOnError { cacheDir(driveFileId).deleteRecursively() }
                .onErrorResumeNext {
                    super.fetchPageList(chapter)
                }
        } else {
            super.fetchPageList(chapter)
                .onErrorResumeNext { e ->
                    if (canUseZip) {
                        Observable.fromCallable { zipPageList(driveFileId) }
                            .doOnError { cacheDir(driveFileId).deleteRecursively() }
                            .onErrorResumeNext {
                                Observable.error(e)
                            }
                    } else {
                        Observable.error(e)
                    }
                }
                .flatMap { pages ->
                    if (pages.isEmpty() && canUseZip) {
                        Observable.fromCallable { zipPageList(driveFileId) }
                            .doOnError { cacheDir(driveFileId).deleteRecursively() }
                            .onErrorReturn { pages }
                    } else {
                        Observable.just(pages)
                    }
                }
        }
    }

    private fun cacheDir(driveFileId: String): File {
        val context = Injekt.get<Application>()
        return context.cacheDir.resolve("source_$id/$driveFileId")
    }

    private fun zipPageList(driveFileId: String): List<Page> {
        val cacheDir = cacheDir(driveFileId)
            .also {
                it.deleteRecursively()
                it.mkdirs()
            }

        val driveLink = "https://drive.google.com/uc".toHttpUrl().newBuilder()
            .addQueryParameter("export", "download")
            .addQueryParameter("id", driveFileId)
            .build()

        client.newCall(GET(driveLink)).execute()
            .let { handleDriveRedirect(it) }
            .use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                ZipInputStream(response.body.byteStream().buffered()).use { zis ->
                    generateSequence { zis.nextEntry }
                        .filter { !it.isDirectory && !it.name.contains('/') && !it.name.endsWith(".xml") }
                        .forEach { entry ->
                            File(cacheDir, entry.name).outputStream().use { zis.copyTo(it) }
                        }
                }
            }

        return cacheDir.listFiles()!!
            .filter { it.isFile }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .mapIndexed { index, file ->
                val url = "http://$ZIP_FILE_HOST/".toHttpUrl().newBuilder()
                    .addQueryParameter("path", file.absolutePath)
                    .build().toString()
                Page(index, "", url)
            }
    }

    private fun handleDriveRedirect(response: Response): Response {
        if (!response.header("Content-Type").orEmpty().contains("text/html")) {
            return response
        }

        val document = response.asJsoup()
        val actionUrl = document.selectFirst("#download-form")!!.attr("action")
            .toHttpUrl().newBuilder().apply {
                document.select("#download-form input[type=hidden]").forEach {
                    addQueryParameter(it.attr("name"), it.attr("value"))
                }
            }.build()
        val headers = Headers.headersOf("Referer", response.request.url.toString())

        return client.newCall(GET(actionUrl, headers)).execute()
    }

    private fun zipFileInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != ZIP_FILE_HOST) return chain.proceed(request)

        val file = File(request.url.queryParameter("path")!!)
        if (!file.exists()) throw IOException("File not found: ${file.name}")

        val contentType = when {
            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
            file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            file.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
            else -> "image/jpeg"
        }.toMediaType()

        val buffer = object : okio.ForwardingSource(file.source()) {
            override fun close() {
                super.close()
                file.delete()
                file.parentFile?.let { parent ->
                    if (parent.listFiles().isNullOrEmpty()) parent.delete()
                }
            }
        }.buffer()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(buffer.asResponseBody(contentType, file.length()))
            .build()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return GET("$baseUrl${chapterUrl.url}", rscHeaders)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return "$baseUrl${chapterUrl.url}"
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
        val maps = imageData.maps.toMutableList()

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
        maps.mapIndexedTo(pages) { index, scrambledImage ->
            Page(pages.size + index, chapterUrl, "http://$SCRAMBLED_IMAGE_HOST/#${scrambledImage.toJsonString()}")
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
        val scrambledImage = url.fragment!!.parseAs<ScrambledImage>()
        val (puzzleMode, layout) = scrambledImage.mode.split("_", limit = 2)

        require(scrambledImage.dim.size >= 2) { "Invalid dim: ${scrambledImage.dim}" }

        val width = scrambledImage.dim[0]
        val height = scrambledImage.dim[1]

        val orderedPieces = scrambledImage.order.map { scrambledImage.pieces[it] }
        val pieceBitmaps = runBlocking {
            orderedPieces.map { pieceUrl ->
                async(Dispatchers.IO.limitedParallelism(2)) {
                    var imgUrl = pieceUrl.toHttpUrl()
                    if (imgUrl.host.startsWith("cdn")) {
                        val payload = Url(url = pieceUrl).toJsonString().toRequestBody(JSON_MEDIA_TYPE)
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
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream())
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(buffer.asResponseBody("image/png".toMediaType(), buffer.size))
                .build()
        } finally {
            pieceBitmaps.forEach { it.recycle() }
            resultBitmap.recycle()
        }
    }

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
private const val ZIP_FILE_HOST = "127.0.0.2"

private val MOBILE_REGEX = Regex("mobile|android|iphone|ipad|ipod", RegexOption.IGNORE_CASE)
private val TABLES_REGEX = Regex("tablet", RegexOption.IGNORE_CASE)
