package eu.kanade.tachiyomi.extension.ar.mangapro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import tachiyomi.decoder.ImageDecoder
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ProChan : HttpSource() {

    override val name = "ProCchan"
    override val baseUrl = "https://procomic.pro"
    override val lang = "ar"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    companion object {
        private const val SCRAMBLED_SCHEME = "https://procomic.pro/__scrambled__/?map="
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            if (!url.startsWith(SCRAMBLED_SCHEME)) return@addInterceptor chain.proceed(request)

            val encoded = url.removePrefix(SCRAMBLED_SCHEME)
            val mapJson = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP))
            val pageMap = json.decodeFromString<ScrambledMap>(mapJson)

            val mergedBytes = reconstructPage(pageMap)
                ?: return@addInterceptor Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Error")
                    .body("".toResponseBody(null))
                    .build()

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(mergedBytes.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/public/content/latest-updates".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "30")
            .addQueryParameter("category", "comics")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<LatestUpdatesResponse>()
        val mangas = data.data.filter { it.type != "novel" }.map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 30)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/public/content/latest-updates".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "30")
            .addQueryParameter("category", "comics")
            .addQueryParameter("page", page.toString())
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val parts = manga.url.split("/")
        val type = parts.getOrElse(0) { "manga" }
        val id = parts.getOrElse(1) { "0" }
        return GET("$baseUrl/api/public/$type/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return try {
            val data = response.parseAs<SeriesDetailResponse>()
            val parts = response.request.url.toString().split("/")
            val idx = parts.indexOf("public")
            val type = parts.getOrElse(idx + 1) { "manga" }
            val id = parts.getOrElse(idx + 2) { "0" }
            SManga.create().apply {
                url = "$type/$id/${data.slug ?: ""}"
                title = data.title ?: ""
                thumbnail_url = data.coverImage
                author = data.author
                artist = data.artist
                description = data.synopsis ?: data.description
                status = when (data.status?.lowercase()) {
                    "ongoing", "مستمر" -> SManga.ONGOING
                    "completed", "مكتمل" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
            }
        } catch (e: Exception) {
            SManga.create()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val parts = manga.url.split("/")
        val type = parts.getOrElse(0) { "manga" }
        val id = parts.getOrElse(1) { "0" }
        val url = "$baseUrl/api/public/$type/$id/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "500")
            .addQueryParameter("order", "desc")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val parts = response.request.url.toString().split("/")
        val idx = parts.indexOf("public")
        val seriesType = parts.getOrElse(idx + 1) { "manga" }
        val seriesId = parts.getOrElse(idx + 2) { "0" }
        val data = response.parseAs<ChaptersResponse>()
        return data.data.map { ch ->
            SChapter.create().apply {
                url = "$seriesType/$seriesId/${ch.id}/${ch.chapterNumber}"
                name = "الفصل ${ch.chapterNumber}" +
                    (if (!ch.title.isNullOrBlank()) " - ${ch.title}" else "")
                date_upload = runCatching {
                    dateFormat.parse(ch.publishedAt ?: "")?.time
                }.getOrNull() ?: 0L
                chapter_number = ch.chapterNumber.toFloatOrNull() ?: 0f
                scanlator = if (ch.lockedByCoins == true) "🔒 مدفوع" else null
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("/")
        val seriesType = parts.getOrElse(0) { "manga" }
        val seriesId = parts.getOrElse(1) { "0" }
        val chapterId = parts.getOrElse(2) { "0" }
        val chapterNumber = parts.getOrElse(3) { "1" }

        val slugResp = client.newCall(
            GET("$baseUrl/api/public/$seriesType/$seriesId", headers),
        ).execute()
        val slug = try {
            slugResp.parseAs<SeriesDetailResponse>().slug ?: seriesId
        } catch (e: Exception) {
            seriesId
        }

        return GET(
            "$baseUrl/series/$seriesType/$seriesId/$slug/$chapterId/$chapterNumber",
            headers.newBuilder()
                .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .set("Referer", "$baseUrl/series/$seriesType/$seriesId/$slug")
                .build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val urlParts = response.request.url.toString().split("/")
        val chapterId = urlParts.getOrElse(urlParts.size - 2) { "0" }

        val token = Regex(
            """eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\.[a-zA-Z0-9_\-]+\.[a-zA-Z0-9_\-]+""",
        ).find(html)?.value ?: return emptyList()

        val apiHeaders = headers.newBuilder().set("Accept", "application/json").build()

        val firstResp = client.newCall(
            GET("$baseUrl/chapter-deferred-media/$chapterId?token=$token&split=0", apiHeaders),
        ).execute()
        val firstResult = firstResp.parseAs<ChapterDeferredResponse>()
        if (!firstResult.success || firstResult.data == null) return emptyList()

        val splitIndex = firstResult.data.splitIndex
        val allSplitData = mutableListOf(firstResult.data)

        for (s in 1..splitIndex) {
            try {
                val resp = client.newCall(
                    GET("$baseUrl/chapter-deferred-media/$chapterId?token=$token&split=$s", apiHeaders),
                ).execute()
                val result = resp.parseAs<ChapterDeferredResponse>()
                if (result.success && result.data != null) {
                    allSplitData.add(result.data)
                }
            } catch (e: Exception) {
                break
            }
        }

        val pages = mutableListOf<Page>()
        var index = 0
        val seenUrls = mutableSetOf<String>()

        for (splitData in allSplitData) {
            val allPieceUrls = splitData.maps.flatMap { it.pieces }.toSet()

            splitData.images.forEach { url ->
                if (url !in allPieceUrls && seenUrls.add(url)) {
                    pages.add(Page(index++, imageUrl = url))
                }
            }

            splitData.maps.forEach { map ->
                if (map.pieces.isEmpty()) return@forEach
                if (!seenUrls.add(map.pieces.first())) return@forEach

                val encoded = Base64.encodeToString(
                    json.encodeToString(
                        ScrambledMap(
                            dim = map.dim,
                            mode = map.mode,
                            pieces = map.pieces,
                            order = map.order,
                        ),
                    ).toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP,
                )
                pages.add(Page(index++, imageUrl = "$SCRAMBLED_SCHEME$encoded"))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response) = ""

    private fun reconstructPage(map: ScrambledMap): ByteArray? {
        val totalW = map.dim.getOrElse(0) { 800 }
        val totalH = map.dim.getOrElse(1) { 1200 }

        val (cols, rows) = parseMode(map.mode, map.pieces.size)

        val bitmaps = arrayOfNulls<Bitmap>(map.pieces.size)
        try {
            for (i in map.pieces.indices) {
                val req = Request.Builder()
                    .url(map.pieces[i])
                    .header("Referer", "$baseUrl/")
                    .header("Accept", "image/avif,*/*")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                val resp = client.newCall(req).execute()
                val bytes = resp.body.bytes()
                resp.close()
                bitmaps[i] = decodeAvif(bytes)
            }

            val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            for (targetIdx in map.pieces.indices) {
                val srcIdx = map.order.getOrElse(targetIdx) { targetIdx }
                val bmp = bitmaps[srcIdx] ?: continue

                val pieceW = bmp.width
                val pieceH = bmp.height

                val col = targetIdx % cols
                val row = targetIdx / cols

                val left = col * pieceW
                val top = row * pieceH

                val dst = Rect(
                    left,
                    top,
                    left + pieceW,
                    top + pieceH
                )

                canvas.drawBitmap(bmp, null, dst, null)
            }

            val out = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, out)
            result.recycle()
            bitmaps.forEach { it?.recycle() }

            return out.toByteArray()
        } catch (e: Exception) {
            bitmaps.forEach { it?.recycle() }
            return null
        }
    }

    private fun decodeAvif(bytes: ByteArray): Bitmap? {
        val decoder = ImageDecoder.newInstance(bytes.inputStream()) ?: run {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        return try {
            decoder.decode()
        } catch (e: Exception) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            decoder.recycle()
        }
    }

    private fun parseMode(mode: String, pieceCount: Int): Pair<Int, Int> {
        return when {
            mode.startsWith("grid_") -> {
                val parts = mode.removePrefix("grid_").split("x")
                Pair(
                    parts.getOrNull(0)?.toIntOrNull() ?: 1,
                    parts.getOrNull(1)?.toIntOrNull() ?: 1,
                )
            }
            mode.startsWith("vertical_") -> {
                Pair(1, mode.removePrefix("vertical_").toIntOrNull() ?: pieceCount)
            }
            else -> Pair(1, pieceCount)
        }
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromStream(body.byteStream())

    @Serializable
    data class ScrambledMap(
        val dim: List<Int> = emptyList(),
        val mode: String = "",
        val pieces: List<String> = emptyList(),
        val order: List<Int> = emptyList(),
    )

    @Serializable
    data class LatestUpdatesResponse(
        val success: Boolean = false,
        val data: List<SeriesDto> = emptyList(),
    )

    @Serializable
    data class SeriesDto(
        @SerialName("mangaId") val id: Int = 0,
        @SerialName("mangaSlug") val slug: String = "",
        @SerialName("mangaTitle") val title: String = "",
        val coverImage: String? = null,
        val type: String = "manga",
        val coverImageApp: CoverImageApp? = null,
    ) {
        fun toSManga() = SManga.create().apply {
            url = "$type/$id/$slug"
            title = this@SeriesDto.title
            thumbnail_url = coverImageApp?.card?.mobile
                ?: coverImageApp?.desktop
                ?: coverImage
        }
    }

    @Serializable
    data class CoverImageApp(val desktop: String? = null, val card: CardImages? = null)

    @Serializable
    data class CardImages(val mobile: String? = null, val desktop: String? = null)

    @Serializable
    data class SeriesDetailResponse(
        val id: Int = 0,
        val title: String? = null,
        val slug: String? = null,
        val coverImage: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val synopsis: String? = null,
        val status: String? = null,
    )

    @Serializable
    data class ChaptersResponse(
        val data: List<ChapterDto> = emptyList(),
        val total: Int = 0,
    )

    @Serializable
    data class ChapterDto(
        val id: Int = 0,
        @SerialName("chapter_number") val chapterNumber: String = "0",
        val title: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val lockedByCoins: Boolean? = null,
    )

    @Serializable
    data class ChapterDeferredResponse(
        val success: Boolean = false,
        val data: ChapterDeferredData? = null,
    )

    @Serializable
    data class ChapterDeferredData(
        val chapterId: Int = 0,
        val splitIndex: Int = 0,
        val images: List<String> = emptyList(),
        val maps: List<PageMap> = emptyList(),
    )

    @Serializable
    data class PageMap(
        val dim: List<Int> = emptyList(),
        val mode: String = "",
        val pieces: List<String> = emptyList(),
        val order: List<Int> = emptyList(),
        val token: String = "",
    )
}
