package eu.kanade.tachiyomi.extension.ar.mangapro

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
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
import java.util.zip.ZipFile

class ProChan : HttpSource() {
    override val name = "ProChan"
    override val lang = "ar"
    override val baseUrl = "https://prochan.net"
    override val supportsLatest = true
    override val versionId = 5

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::zipFileInterceptor)
        .addInterceptor(::scrambledImageInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 2
        }

        return fetchSearchManga(page, "", filters)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 0
        }

        return fetchSearchManga(page, "", filters)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            // TODO
            throw Exception("not implemented")
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val types = setOf("manga", "manhwa", "manhua")
                val statusFilter = filters.firstInstance<StatusFilter>().selected
                val genreFilter = filters.firstInstance<GenreFilter>()

                val mangas = response.parseAs<Data<List<BrowseManga>>>().data.asSequence()
                    .filter { manga ->
                        manga.type in types
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
                    .map { manga ->
                        SManga.create().apply {
                            url = "/series/${manga.type}/${manga.id}/${manga.slug}"
                            title = manga.title
                            thumbnail_url = manga.coverImageApp?.desktop ?: manga.coverImage
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
    )

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val type = mangaUrl.pathSegments[1]
        val id = mangaUrl.pathSegments[2]

        return GET("$baseUrl/api/public/$type/$id", headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseAs<Manga>()

        return SManga.create().apply {
            url = "/series/${manga.type}/${manga.id}/${manga.slug}"
            title = manga.title
            artist = manga.metadata.artist
                ?.split("\n")
                ?.joinToString(transform = String::trim)
            author = manga.metadata.author
                ?.split("\n")
                ?.joinToString(transform = String::trim)
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
                val genreMap = genres.associate { it.second to it.first }
                manga.metadata.genres.mapTo(this) { genreMap[it] ?: it }
            }.joinToString()
            status = when (manga.progress?.trim()) {
                "مستمر" -> SManga.ONGOING
                "مكتمل" -> SManga.COMPLETED
                "متوقف" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = manga.metadata.coverImage
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ChapterList>()

        return data.chapters
            .filter { it.language == "AR" }
            .map { chapter ->
                SChapter.create().apply {
                    url = ChapterUrl(
                        url = "/series/${data.type}/${data.id}/${data.slug}/${chapter.id}/${chapter.number}",
                        driveFileId = chapter.metadata.driveFileId,
                    ).toJsonString()
                    name = buildString {
                        if (chapter.coins != null && chapter.coins > 0) {
                            append("🔒 ")
                        }
                        append("الفصل ")
                        append(chapter.number)
                        chapter.title?.also {
                            append(" - ", it.trim())
                        }
                    }
                    scanlator = chapter.uploader ?: "\u200B"
                    chapter_number = chapter.number.toFloat()
                    date_upload = dateFormat.tryParse(chapter.createdAt)
                }
            }.asReversed()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return if (chapterUrl.driveFileId != null) {
            Observable.just(zipPageList(chapterUrl.driveFileId))
        } else {
            super.fetchPageList(chapter)
        }
    }

    private fun zipPageList(driveFileId: String): List<Page> {
        val driveLink = "https://drive.google.com/uc".toHttpUrl().newBuilder()
            .addQueryParameter("export", "download")
            .addQueryParameter("id", driveFileId)
            .build()

        val context = Injekt.get<Application>()

        val cacheDir = context.cacheDir
            .resolve("source_$id")
            .also { it.mkdirs() }
        val zipFile = File(cacheDir, "$driveFileId.zip")

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "$name: Loading Chapter...\nMay take a few minutes!!", Toast.LENGTH_LONG).show()
        }

        client.newCall(GET(driveLink)).execute().let { response ->
            if (response.header("Content-Type").orEmpty().contains("text/html")) {
                val document = response.asJsoup()
                val actionUrl = document.selectFirst("#download-form")!!.attr("action")
                    .toHttpUrl().newBuilder().apply {
                        document.select("#download-form input[type=hidden]").forEach {
                            addQueryParameter(it.attr("name"), it.attr("value"))
                        }
                    }.build()

                val headers = Headers.headersOf("Referer", response.request.url.toString())
                client.newCall(GET(actionUrl, headers)).execute()
            } else {
                response
            }
        }.use { zipResponse ->
            zipResponse.body.byteStream().use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val files = ZipFile(zipFile).use { file ->
            file.entries().asSequence()
                .map { entry -> entry.name }
                .filterNot { it.endsWith(".xml") }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                .toList()
        }

        return files.map {
            val url = "http://${ZIP_FILE_HOST}/".toHttpUrl().newBuilder()
                .addQueryParameter("path", zipFile.absolutePath)
                .addQueryParameter("filename", it)
                .build().toString()
            Page(0, driveLink.toString(), url)
        }
    }

    private fun zipFileInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != ZIP_FILE_HOST) return chain.proceed(request)

        val path = request.url.queryParameter("path")!!
        val filename = request.url.queryParameter("filename")!!
        val zipFile = File(path)

        if (!zipFile.exists()) throw IOException("File not found")

        val zip = ZipFile(zipFile)
        val entry = zip.getEntry(filename) ?: throw IOException("Entry not found")

        val source = object : okio.ForwardingSource(
            zip.getInputStream(entry).source(),
        ) {
            override fun close() {
                super.close()
                zip.close()
            }
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(object : ResponseBody() {
                override fun contentType() = "image/jpeg".toMediaType()
                override fun contentLength() = entry.size
                override fun source() = source.buffer()
            })
            .build()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return GET("$baseUrl${chapterUrl.url}", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return "$baseUrl${chapterUrl.url}"
    }

    override fun pageListParse(response: Response): List<Page> {
        val script = response.asJsoup()
            .select("script:containsData(self.__next_f.push)")
            .joinToString(";") { it.data() }

        val images = IMAGES_REGEX.find(script)!!.groupValues[1]
            .unescape()
            .parseAs<List<String>>()

        val maps = MAP_IMAGES_REGEX.find(script)!!.groupValues[1]
            .unescape()
            .parseAs<List<MappedImage>>()

        val chapterUrl = response.request.url.toString()

        val pages = mutableListOf<Page>()

        images.mapTo(pages) { imageUrl ->
            Page(0, chapterUrl, imageUrl)
        }

        maps.mapTo(pages) { mapped ->
            Page(0, chapterUrl, "http://$MAPPED_IMAGE_HOST/#${mapped.toJsonString()}")
        }

        return pages
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, headers)
    }

    fun String.unescape(): String = UNESCAPE_REGEX.replace(this, "$1")

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (request.url.host != MAPPED_IMAGE_HOST) {
            return chain.proceed(request)
        }

        val chapterUrl = request.header("Referer")!!

        val mappedImage = url.fragment!!.parseAs<MappedImage>()
        val (puzzleMode, layout) = mappedImage.mode.split("_", limit = 2)

        val orderedPieces = mappedImage.order.map { mappedImage.pieces[it] }
        val pieceBitmaps = orderedPieces.map { pieceUrl ->
            var imgUrl = pieceUrl.toHttpUrl()

            if (imgUrl.host.startsWith("cdn")) {
                val payload = Url(url = pieceUrl)
                    .toJsonString()
                    .toRequestBody(JSON_MEDIA_TYPE)
                val headers = headersBuilder()
                    .set("Sec-Fetch-Site", "same-origin")
                    .set("Referer", chapterUrl)
                    .build()
                val request = POST("$baseUrl/api/cdn-image/sign", headers, payload)

                val token = client.newCall(request).execute().parseAs<Token>()

                imgUrl = imgUrl.newBuilder()
                    .setQueryParameter("token", token.token)
                    .setQueryParameter("expires", token.expires.toString())
                    .build()
            }

            val pieceRequest = request.newBuilder().url(imgUrl).build()
            val response = chain.proceed(pieceRequest)

            // use Tachiyomi ImageDecoder because android.graphics.BitmapFactory doesn't handle avif
            response.body.use { body ->
                val decoder = ImageDecoder.newInstance(body.byteStream())
                    ?: throw Exception("Failed to create decoder")
                try {
                    decoder.decode() ?: throw Exception("Failed to decode piece")
                } finally {
                    decoder.recycle()
                }
            }
        }

        val width = mappedImage.dim[0]
        val height = mappedImage.dim[1]
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        when (puzzleMode) {
            "vertical" -> {
                var x = 0f
                for (bitmap in pieceBitmaps) {
                    canvas.drawBitmap(bitmap, x, 0f, null)
                    x += bitmap.width
                }
            }

            "grid" -> {
                val (cols, rows) = layout.split('x', limit = 2)
                    .map { it.toInt() }

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
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private val UNESCAPE_REGEX = """\\(.)""".toRegex()
private val IMAGES_REGEX = """self\.__next_f\.push\(.*images\\":(\[[^]]+])""".toRegex()
private val MAP_IMAGES_REGEX = """self\.__next_f\.push\(.*maps\\":(\[.*]),\\"app""".toRegex()
private const val MAPPED_IMAGE_HOST = "127.0.0.1"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private const val ZIP_FILE_HOST = "127.0.0.2"
