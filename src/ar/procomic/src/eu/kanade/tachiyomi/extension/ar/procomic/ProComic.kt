package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

class ProComic : HttpSource() {

    override val name = "ProComic"
    override val baseUrl = "https://procomic.pro"
    override val lang = "ar"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    companion object {
        private const val SCRAMBLED_SCHEME = "https://procomic.pro/__scrambled__/?map="
        private const val MAX_SAFE_HEIGHT = 6000
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .rateLimit(2, 1)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (!url.startsWith(SCRAMBLED_SCHEME)) return@addInterceptor chain.proceed(request)

            val encoded = url.removePrefix(SCRAMBLED_SCHEME)
            val mapJson = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP))
            val pageMap = json.decodeFromString<ScrambledMap>(mapJson)

            val mergedBytes = reconstructPage(pageMap)
                ?: return@addInterceptor Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1)
                    .code(500).message("Error")
                    .body("".toResponseBody(null)).build()

            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(mergedBytes.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept-Language", "ar-EG,ar;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

    override fun popularMangaRequest(page: Int) = GET(
        "$baseUrl/api/public/content/latest-updates?limit=30&category=comics&page=$page",
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<LatestUpdatesResponse>()
        val mangas = data.data.filter { it.type != "novel" }.map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 30)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET(
        "$baseUrl/api/public/content/latest-updates?limit=30&category=comics&page=$page" +
            (if (query.isNotBlank()) "&q=$query" else ""),
        headers,
    )

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val p = manga.url.split("/")
        return GET("$baseUrl/api/public/${p[0]}/${p[1]}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return try {
            val data = response.parseAs<SeriesDetailResponse>()
            val parts = response.request.url.pathSegments
            val idx = parts.indexOf("public")
            SManga.create().apply {
                url = "${parts.getOrElse(idx + 1) { "manga" }}/${parts.getOrElse(idx + 2) { "0" }}/${data.slug ?: ""}"
                title = data.title ?: ""
                thumbnail_url = data.coverImageApp?.card?.mobile ?: data.coverImageApp?.desktop ?: data.coverImage
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
        } catch (e: Exception) { SManga.create() }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val p = manga.url.split("/")
        return GET(
            "$baseUrl/api/public/${p[0]}/${p[1]}/chapters?page=1&limit=500&order=desc",
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val parts = response.request.url.pathSegments
        val idx = parts.indexOf("public")
        val seriesType = parts.getOrElse(idx + 1) { "manga" }
        val seriesId = parts.getOrElse(idx + 2) { "0" }

        return response.parseAs<ChaptersResponse>().data.map { ch ->
            SChapter.create().apply {
                url = "$seriesType/$seriesId/${ch.id}/${ch.chapterNumber}"
                name = "الفصل ${ch.chapterNumber}" + (if (!ch.title.isNullOrBlank()) " - ${ch.title}" else "")
                date_upload = runCatching { dateFormat.parse(ch.publishedAt ?: "")?.time }.getOrNull() ?: 0L
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

        val url = "$baseUrl/api/public/$seriesType/$seriesId/chapters".toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "500")
            .addQueryParameter("order", "desc")
            .addQueryParameter("_cid", chapterId)
            .build()

        return GET(url, headers.newBuilder().set("Accept", "application/json").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.queryParameter("_cid") ?: return emptyList()
        val seriesType = response.request.url.pathSegments.let { parts ->
            val idx = parts.indexOf("public")
            parts.getOrElse(idx + 1) { "manga" }
        }
        val seriesId = response.request.url.pathSegments.let { parts ->
            val idx = parts.indexOf("public")
            parts.getOrElse(idx + 2) { "0" }
        }

        val apiHeaders = headers.newBuilder().set("Accept", "application/json").build()
        val pages = mutableListOf<Page>()
        val seenUrls = mutableSetOf<String>()

        var cdnPath = "cdn1"
        var metadataImages = emptyList<String>()
        val mapsList = mutableListOf<DeferredPageMap>()
        var found = false

        var cachedSessionKey: String? = null
        var sessionKeyAttempted = false
        val getSessionKey = {
            if (!sessionKeyAttempted) {
                sessionKeyAttempted = true
                try {
                    val req = client.newCall(GET("$baseUrl/chapter-map-session-key/$chapterId?legacy=1", apiHeaders)).execute()
                    if (req.isSuccessful) {
                        cachedSessionKey = req.parseAs<SessionKeyResponse>().data?.key
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            cachedSessionKey
        }

        val currentData = try { response.parseAs<ChaptersResponse>() } catch (e: Exception) { ChaptersResponse() }
        for (ch in currentData.data) {
            if (ch.id.toString() == chapterId) {
                cdnPath = ch.cdnPath ?: "cdn1"
                metadataImages = ch.metadata?.images ?: emptyList()
                ch.metadata?.maps?.let { mapsList.addAll(it) }
                found = true
                break
            }
        }

        if (!found) {
            var pg = 2
            outer@ while (pg <= 10) {
                try {
                    val resp = client.newCall(
                        GET("$baseUrl/api/public/$seriesType/$seriesId/chapters?limit=500&page=$pg&order=desc", apiHeaders),
                    ).execute()
                    if (!resp.isSuccessful) break
                    val data = resp.parseAs<ChaptersResponse>()
                    if (data.data.isEmpty()) break
                    for (ch in data.data) {
                        if (ch.id.toString() == chapterId) {
                            cdnPath = ch.cdnPath ?: "cdn1"
                            metadataImages = ch.metadata?.images ?: emptyList()
                            ch.metadata?.maps?.let { mapsList.addAll(it) }
                            break@outer
                        }
                    }
                } catch (e: Exception) {
                    break
                }
                pg++
            }
        }

        val cdnBase = "https://$cdnPath.procomic.pro"
        val mapTokens = mutableListOf<String>()

        metadataImages.forEach { imgPath ->
            val fullUrl = if (imgPath.startsWith("http")) imgPath else "$cdnBase$imgPath"
            if (seenUrls.add(fullUrl)) pages.add(Page(pages.size, imageUrl = fullUrl))
        }

        mapsList.forEach { map ->
            if (map.pieces.isNotEmpty()) {
                val absolutePieces = map.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" }
                processMap(map.dim, map.mode, absolutePieces, map.order, map.token, pages, seenUrls)
            } else if (map.method == "browser_session" && map.token.isNotBlank()) {
                val sk = getSessionKey()
                if (sk != null) {
                    val dec = decryptMap(map.token, sk)
                    if (dec != null && dec.pieces.isNotEmpty()) {
                        val absolutePieces = dec.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" }
                        processMap(dec.dim, dec.mode, absolutePieces, dec.order, dec.token, pages, seenUrls)
                    }
                }
            } else if (map.token.isNotBlank()) {
                mapTokens.add(map.token)
            }
        }

        for (jwtToken in mapTokens) {
            try {
                val newPages = fetchDeferredPages(chapterId, jwtToken, apiHeaders, seenUrls, cdnBase, getSessionKey)
                pages.addAll(newPages)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // --- كود دعم النظام الجديد بدأ من هنا ---
        if (pages.isEmpty()) {
            try {
                // نرسل طلب POST لمعرفة الصفحات باستخدام النظام الجديد (Proxy Plan)
                val body = "{}".toRequestBody("application/json".toMediaType())
                val proxyReq = POST("$baseUrl/chapter-map-proxy-plan/$chapterId", apiHeaders, body)
                val proxyResp = client.newCall(proxyReq).execute()

                if (proxyResp.isSuccessful) {
                    val proxyData = proxyResp.parseAs<ProxyPlanResponse>()
                    val map = proxyData.data?.map
                    if (map != null && map.pieces.isNotEmpty()) {
                        val absolutePieces = map.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" }
                        processMap(map.dim, map.mode, absolutePieces, map.order, map.token, pages, seenUrls)
                    }
                }
            } catch (e: Exception) {
                // في حال فشل النظام الجديد، سيتم تخطيه
            }
        }
        // --- نهاية الكود الجديد ---

        return pages
    }

    private fun processMap(
        dim: List<Int>,
        mode: String,
        pieces: List<String>,
        order: List<Int>,
        signedToken: String,
        pages: MutableList<Page>,
        seenUrls: MutableSet<String>,
    ) {
        if (pieces.isEmpty() || !seenUrls.add(pieces.first())) return

        val estimatedTotalH = dim.getOrNull(1)?.takeIf { it > 0 } ?: 10000
        val parts = if (estimatedTotalH > MAX_SAFE_HEIGHT) {
            ceil(estimatedTotalH.toDouble() / MAX_SAFE_HEIGHT).toInt()
        } else {
            1
        }

        for (p in 0 until parts) {
            val encoded = Base64.encodeToString(
                json.encodeToString(
                    ScrambledMap(
                        dim = dim,
                        mode = mode,
                        pieces = pieces,
                        order = order,
                        signedToken = signedToken,
                        splitPart = p,
                        totalParts = parts,
                    ),
                ).toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP,
            )
            pages.add(Page(pages.size, imageUrl = "$SCRAMBLED_SCHEME$encoded"))
        }
    }

    private fun fetchDeferredPages(
        chapterId: String,
        jwtToken: String,
        apiHeaders: Headers,
        seenUrls: MutableSet<String>,
        cdnBase: String,
        getSessionKey: () -> String?,
    ): List<Page> {
        val pages = mutableListOf<Page>()

        val first = client.newCall(
            GET("$baseUrl/chapter-deferred-media/$chapterId?token=$jwtToken&split=0", apiHeaders),
        ).execute()
        if (!first.isSuccessful) return pages

        val firstData = first.parseAs<ChapterDeferredResponse>()
        if (!firstData.success || firstData.data == null) return pages

        val splits = mutableListOf(firstData.data)
        for (s in 1..firstData.data.splitIndex) {
            try {
                val r = client.newCall(
                    GET("$baseUrl/chapter-deferred-media/$chapterId?token=$jwtToken&split=$s", apiHeaders),
                ).execute()
                if (!r.isSuccessful) break
                val d = r.parseAs<ChapterDeferredResponse>()
                if (d.success && d.data != null) splits.add(d.data)
            } catch (e: Exception) {
                break
            }
        }

        for (split in splits) {
            val decryptedMaps = mutableListOf<DeferredPageMap>()
            val absolutePieceUrls = mutableSetOf<String>()

            split.maps.forEach { map ->
                if (map.pieces.isNotEmpty()) {
                    decryptedMaps.add(map)
                    absolutePieceUrls.addAll(map.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" })
                } else if (map.method == "browser_session" && map.token.isNotBlank()) {
                    val sk = getSessionKey()
                    if (sk != null) {
                        val dec = decryptMap(map.token, sk)
                        if (dec != null && dec.pieces.isNotEmpty()) {
                            decryptedMaps.add(dec)
                            absolutePieceUrls.addAll(dec.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" })
                        }
                    }
                }
            }

            split.images.forEach { url ->
                val fullUrl = if (url.startsWith("http")) url else "$cdnBase$url"
                if (fullUrl !in absolutePieceUrls && seenUrls.add(fullUrl)) {
                    pages.add(Page(pages.size, imageUrl = fullUrl))
                }
            }

            decryptedMaps.forEach { map ->
                val absolutePieces = map.pieces.map { if (it.startsWith("http")) it else "$cdnBase$it" }
                processMap(map.dim, map.mode, absolutePieces, map.order, map.token, pages, seenUrls)
            }
        }
        return pages
    }

    override fun imageUrlParse(response: Response) = ""

    private fun decryptMap(tokenStr: String, sessionKeyBase64: String): DeferredPageMap? {
        return try {
            val tokenJsonStr = String(Base64.decode(tokenStr, Base64.URL_SAFE or Base64.DEFAULT))
            val tokenData = json.decodeFromString<EncryptedToken>(tokenJsonStr)

            val keyBytes = Base64.decode(sessionKeyBase64, Base64.URL_SAFE)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val ivBytes = Base64.decode(tokenData.iv, Base64.URL_SAFE)
            val tagBytes = Base64.decode(tokenData.tag, Base64.URL_SAFE)
            val cipherTextBytes = Base64.decode(tokenData.data, Base64.URL_SAFE)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

            val decryptedBytes = cipher.doFinal(cipherTextBytes + tagBytes)
            json.decodeFromString<DeferredPageMap>(String(decryptedBytes))
        } catch (e: Exception) {
            null
        }
    }

    private fun reconstructPage(map: ScrambledMap): ByteArray? {
        if (map.pieces.isEmpty()) return null

        val (cols, rows) = parseMode(map.mode, map.pieces.size)
        val bitmaps = arrayOfNulls<Bitmap>(map.pieces.size)

        for (targetIdx in 0 until map.pieces.size) {
            val srcIdx = if (map.order.size == map.pieces.size) map.order[targetIdx] else targetIdx
            val basePieceUrl = map.pieces.getOrNull(srcIdx) ?: continue

            val pieceUrl = if (map.signedToken.isNotBlank()) {
                if (basePieceUrl.contains("?")) "$basePieceUrl&token=${map.signedToken}" else "$basePieceUrl?token=${map.signedToken}"
            } else {
                basePieceUrl
            }

            val req = Request.Builder()
                .url(pieceUrl)
                .header("Referer", "$baseUrl/")
                .header("Accept", "image/avif,image/webp,image/jpeg,*/*")
                .header("User-Agent", headers["User-Agent"] ?: "Mozilla/5.0")
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        bitmaps[targetIdx] = decodeAvif(resp.body.bytes())
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            val validBitmaps = bitmaps.filterNotNull()
            if (validBitmaps.isEmpty()) return null

            var calcTotalW = 0
            var calcTotalH = 0

            if (cols == 1) { // Vertical stack (Rows mode)
                calcTotalW = map.dim.getOrNull(0)?.takeIf { it > 0 } ?: validBitmaps.maxOf { it.width }
                calcTotalH = map.dim.getOrNull(1)?.takeIf { it > 0 } ?: validBitmaps.sumOf { it.height }
            } else if (rows == 1) { // Horizontal stack (Columns mode)
                calcTotalW = map.dim.getOrNull(0)?.takeIf { it > 0 } ?: validBitmaps.sumOf { it.width }
                calcTotalH = map.dim.getOrNull(1)?.takeIf { it > 0 } ?: validBitmaps.maxOf { it.height }
            } else { // Grid
                val firstBmp = validBitmaps.first()
                calcTotalW = map.dim.getOrNull(0)?.takeIf { it > 0 } ?: (firstBmp.width * cols)
                calcTotalH = map.dim.getOrNull(1)?.takeIf { it > 0 } ?: (firstBmp.height * rows)
            }

            val totalParts = map.totalParts ?: 1
            val splitPart = map.splitPart ?: 0
            val partH = calcTotalH / totalParts
            val actualPartH = if (splitPart == totalParts - 1) calcTotalH - (partH * splitPart) else partH

            if (calcTotalW <= 0 || actualPartH <= 0) return null

            val result = try {
                Bitmap.createBitmap(calcTotalW, actualPartH, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                Bitmap.createBitmap(calcTotalW, actualPartH, Bitmap.Config.RGB_565)
            }
            val canvas = Canvas(result)

            val startY = splitPart * partH
            canvas.translate(0f, -startY.toFloat())

            if (cols == 1) {
                var currentY = 0f
                for (bmp in bitmaps) {
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, 0f, currentY, null)
                        currentY += bmp.height
                        bmp.recycle()
                    }
                }
            } else if (rows == 1) {
                var currentX = 0f
                for (bmp in bitmaps) {
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, currentX, 0f, null)
                        currentX += bmp.width
                        bmp.recycle()
                    }
                }
            } else {
                val tileW = validBitmaps.first().width
                val tileH = validBitmaps.first().height
                for (targetIdx in bitmaps.indices) {
                    val bmp = bitmaps[targetIdx] ?: continue
                    val col = targetIdx % cols
                    val row = targetIdx / cols
                    canvas.drawBitmap(bmp, (col * tileW).toFloat(), (row * tileH).toFloat(), null)
                    bmp.recycle()
                }
            }

            val out = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 85, out)
            result.recycle()
            return out.toByteArray()
        } catch (e: Exception) {
            return null
        }
    }

    private fun decodeAvif(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val decoder = ImageDecoder.newInstance(bytes.inputStream())
        return if (decoder != null) {
            try {
                decoder.decode()
            } catch (e: Exception) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                decoder.recycle()
            }
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun parseMode(mode: String, pieceCount: Int): Pair<Int, Int> = when {
        mode.startsWith("grid_") -> {
            val clean = mode.removePrefix("grid_")
            val p = if (clean.contains("x")) clean.split("x") else clean.split("_")
            Pair(p.getOrNull(0)?.toIntOrNull() ?: 1, p.getOrNull(1)?.toIntOrNull() ?: 1)
        }
        mode.startsWith("vertical_") -> Pair(mode.removePrefix("vertical_").toIntOrNull() ?: pieceCount, 1)
        mode.startsWith("horizontal_") -> Pair(1, mode.removePrefix("horizontal_").toIntOrNull() ?: pieceCount)
        else -> Pair(1, pieceCount)
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromStream(body.byteStream())
}

@Serializable
data class SessionKeyResponse(
    val success: Boolean = false,
    val data: SessionKeyData? = null,
)

@Serializable
data class SessionKeyData(val key: String = "")

@Serializable
data class EncryptedToken(
    val v: Int = 3,
    val m: String = "",
    val cid: Int = 0,
    val iv: String = "",
    val tag: String = "",
    val data: String = "",
)

@Serializable
data class ScrambledMap(
    val dim: List<Int> = emptyList(),
    val mode: String = "",
    val pieces: List<String> = emptyList(),
    val order: List<Int> = emptyList(),
    val signedToken: String = "",
    val splitPart: Int? = null,
    val totalParts: Int? = null,
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
        thumbnail_url = coverImageApp?.card?.mobile ?: coverImageApp?.desktop ?: coverImage
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
    val coverImageApp: CoverImageApp? = null,
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
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ChapterMetadataDto? = null,
)

@Serializable
data class ChapterMetadataDto(
    val images: List<String> = emptyList(),
    val maps: List<DeferredPageMap> = emptyList(),
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
    val maps: List<DeferredPageMap> = emptyList(),
)

@Serializable
data class DeferredPageMap(
    val dim: List<Int> = emptyList(),
    val mode: String = "",
    val pieces: List<String> = emptyList(),
    val order: List<Int> = emptyList(),
    val token: String = "",
    val method: String = "",
)

// --- كلاسات جديدة لدعم النظام الجديد ---
@Serializable
data class ProxyPlanResponse(
    val success: Boolean = false,
    val data: ProxyPlanData? = null,
)

@Serializable
data class ProxyPlanData(
    val map: DeferredPageMap? = null,
)
