package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import tachiyomi.decoder.ImageDecoder
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Procomic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::scrambledImageInterceptor)
        addNetworkInterceptor(
            CookieInterceptor(
                baseUrl.removePrefix("https://"),
                listOf("safe_browsing" to "off", "language" to "ar"),
            ),
        )
    }

    val rscHeaders by lazy { headersBuilder().add("rsc", "1").build() }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = getFilterList()
        filters.firstInstanceOrNull<SortFilter>()?.state = 0
        return searchApi(page, filters)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = getFilterList()
        filters.firstInstanceOrNull<SortFilter>()?.state = 2
        return searchApi(page, filters)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchApi(page, filters, query)

    private suspend fun searchApi(page: Int, filters: FilterList, query: String = ""): MangasPage {
        val isCatalog = query.isBlank()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val yearFilter = filters.firstInstanceOrNull<YearFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val effectiveSort = sortFilter?.selected ?: "popular"
        val endpoint = if (isCatalog) "api/public/content" else "api/public/series/search"
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(endpoint)
            .addQueryParameter("status", "approved")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", effectiveSort)
            .apply {
                if (!isCatalog) addQueryParameter("search", query)
                typeFilter?.selected?.also { addQueryParameter("type", it) }
                yearFilter?.selected?.also { addQueryParameter("year", it) }
                statusFilter?.selected?.also { addQueryParameter("status", it) }
            }
            .build()
        val data = client.get(url).parseAs<SearchResponse>()
        return MangasPage(
            data.data.filter { it.type in SUPPORTED_TYPES }.map { it.toSManga() },
            data.meta?.let { it.totalPages != null && it.currentPage != null && it.totalPages > it.currentPage } ?: false,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val segs = url.pathSegments
        val off = if (segs.firstOrNull() in LANG_SEGMENTS) 1 else 0
        if (segs.size < off + 4 || segs.getOrNull(off) != "series") return null
        val type = segs[off + 1]
        if (type !in SUPPORTED_TYPES) return null
        val id = segs[off + 2]
        val slug = segs[off + 3]
        return SManga.create().also {
            it.url = id
            it.memo = buildJsonObject {
                put("type", type)
                put("slug", slug)
            }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url
        val m = manga.memo
        val type = m["type"]!!.string
        val apiManga = fetchApiManga(type, id)
        val smanga = apiManga.toSManga()
        val chapterList = (apiManga.chapters ?: emptyList())
            .filter { it.language == "AR" }
            .map { it.toSChapter(type, id, apiManga.slug) }

        return SMangaUpdate(smanga, chapterList)
    }

    private suspend fun fetchApiManga(type: String, id: String): ApiManga {
        val url = "$baseUrl/api/public/$type/$id"
        return client.get(url).parseAs<ApiManga>()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val m = chapter.memo
        val type = m["type"]!!.string
        val seriesId = m["seriesId"]!!.string
        val slug = m["slug"]!!.string
        val chapterNum = chapter.chapter_number
        val chapterId = chapter.url
        return "$baseUrl/series/$type/$seriesId/$slug/$chapterId/$chapterNum"
    }

    override fun getMangaUrl(manga: SManga): String {
        val m = manga.memo
        val id = manga.url
        val type = m["type"]!!.string
        val slug = m["slug"]!!.string
        return "$baseUrl/series/$type/$id/$slug"
    }

    override fun imageRequest(page: Page) = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headersBuilder().set("Referer", page.url).build())
        .get()
        .build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val body = client.get(chapterUrl, rscHeaders).body.string()
        val chapterData = body.extractNextJsRsc<ChapterImages>()
            ?: if (body.contains("\"initialSafeBrowsingEnabled\":true")) {
                throw Exception(
                    "التصفح الآمن مفعّل — هذه السلسلة مخفية. أوقِف التصفح الآمن من إعدادات المستخدم على ProComic.\n" +
                        "Safe browsing is enabled on ProComic — disable it in user settings to read this series.",
                )
            } else {
                return emptyList()
            }

        val pages = mutableListOf<Page>()
        var index = 0

        chapterData.appImages.forEach { img ->
            pages.add(Page(index++, imageUrl = img.mobile ?: img.desktop!!, url = chapterUrl))
        }

        val deferred = chapterData.deferredMedia
        if (deferred != null) {
            val chapterId = chapter.url
            val deferredUrl = "$baseUrl/chapter-deferred-media/$chapterId".toHttpUrl().newBuilder()
                .addQueryParameter("token", deferred.token)
                .build()
            val deferredResp = client.get(deferredUrl.toString(), rscHeaders).parseAs<DeferredResponse>()
            val data = deferredResp.data

            data.images.forEach { url ->
                val imgUrl = signImage(url, chapterUrl) ?: url
                pages.add(Page(index++, imageUrl = imgUrl, url = chapterUrl))
            }

            if (data.maps.isNotEmpty()) {
                data.maps.forEach { mapEntry ->
                    val image = fetchMapPlan(chapter.url, mapEntry, chapterData.cdnPath, index, chapterUrl)
                    pages.add(Page(index++, imageUrl = "http://127.0.0.1/#${image.toJsonString()}", url = chapterUrl))
                }
            }
        }

        return pages
    }

    // -- Scrambled image interceptor --

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != "127.0.0.1") return chain.proceed(request)

        val scrambledImage = url.fragment!!.parseAs<ScrambledImage>()

        require(scrambledImage.dim.size >= 2) { "Invalid dim" }

        val width = scrambledImage.dim[0]
        val height = scrambledImage.dim[1]

        val orderedPieces = scrambledImage.order.map { scrambledImage.pieces[it] }
        val pieceBitmaps = runBlocking {
            orderedPieces.map { pieceUrl ->
                async(Dispatchers.IO.limitedParallelism(2)) {
                    val pieceRequest = request.newBuilder().url(pieceUrl).build()
                    val response = client.newCall(pieceRequest).execute()
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
            }.awaitAll()
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        try {
            val rects = scrambledImage.rects
            require(rects.size == pieceBitmaps.size) { "Rects/pieces mismatch" }
            rects.forEachIndexed { i, rect ->
                canvas.drawBitmap(
                    pieceBitmaps[i],
                    null,
                    Rect(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height),
                    null,
                )
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

    private suspend fun signImage(cdnUrl: String, referer: String): String? {
        val payload = buildJsonObject { put("url", cdnUrl) }.toJsonString()
            .toRequestBody("application/json".toMediaType())

        val rscheaders = rscHeaders.newBuilder()
            .set("Referer", referer)
            .set("Sec-Fetch-Site", "same-origin")
            .build()
        val response = client.post("$baseUrl/api/cdn-image/sign", rscheaders, payload)

        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val sign = response.parseAs<SignResponse>()
        val token = sign.data?.token ?: sign.token ?: return null
        val expires = sign.data?.expires ?: sign.expires ?: return null
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/cdn-image")
            .addQueryParameter("url", cdnUrl)
            .addQueryParameter("token", token)
            .addQueryParameter("expires", expires)
            .build()
            .toString()
    }

    // -- Scrambled map proxy --

    private suspend fun fetchMapPlan(cid: String, entry: MapEntry, cdnPath: String?, pageIndex: Int, referer: String): ScrambledImage {
        val body = buildJsonObject {
            put("token", entry.token)
            put("method", entry.method ?: "browser_session")
            put("cdnPath", cdnPath ?: "cdn2")
            put("pageIndex", pageIndex)
        }.toJsonRequestBody()
        val headers = headersBuilder()
            .set("Origin", baseUrl)
            .set("Referer", referer)
            .build()
        return client.post("$baseUrl/chapter-map-proxy-plan/$cid", headers, body)
            .parseAs<ProxyPlanResponse>().data.map
    }

    // -- Manga helpers --

    private fun ApiManga.toSManga(): SManga = SManga.create().apply {
        url = "$id"
        memo = buildJsonObject {
            put("type", type)
            put("slug", slug)
        }
        title = this@toSManga.title
        metadata?.let { meta ->
            artist = meta.artist
            author = meta.author
            description = buildString {
                this@toSManga.description?.let { append(it.trim(), "\n\n") }
                val extras = buildList {
                    meta.originalTitle?.also { add(it) }
                    meta.altTitles?.forEach { add(it) }
                }
                if (extras.isNotEmpty()) {
                    append("عناوين بديلة\n")
                    extras.forEach { append("- ", it, "\n") }
                }
            }.trim()
            genre = buildList {
                add(type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                meta.year?.also { add(it) }
                meta.origin?.let { origin -> add(origin.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }) }
                when (type) {
                    "manga" -> add("مانجا")
                    "manhwa" -> add("مانها")
                    "manhua" -> add("مانهوا")
                }
                meta.genres.orEmpty().forEach { add(it) }
                meta.tags.orEmpty().forEach { add(it) }
            }.joinToString()
            status = when (progress?.trim()) {
                "مستمر" -> SManga.ONGOING
                "مكتمل" -> SManga.COMPLETED
                "متوقف" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
        thumbnail_url = (coverImageApp?.desktop ?: metadata?.coverImage)?.let {
            if (it.startsWith("/")) "$baseUrl$it" else it
        }
        initialized = true
    }

    private fun SearchItem.toSManga(): SManga = SManga.create().apply {
        url = "$id"
        memo = buildJsonObject {
            put("type", type)
            put("slug", slug)
        }
        title = this@toSManga.title
        thumbnail_url = (coverImageApp?.desktop ?: coverImage)?.let {
            if (it.startsWith("/")) "$baseUrl$it" else it
        }
    }

    private fun ApiChapter.toSChapter(type: String, seriesId: String, slug: String): SChapter = SChapter.create().apply {
        url = "$id"
        memo = buildJsonObject {
            put("type", type)
            put("seriesId", seriesId)
            put("slug", slug)
        }
        name = buildString {
            append("\u200F")
            if (coins != null && coins > 0) append("\uD83D\uDD12 ")
            append("الفصل ")
            append(chapterNumber.toFloatOrNull()?.toString()?.substringBefore(".0") ?: chapterNumber)
            title?.trim()?.takeIf { t -> t.isNotBlank() && t != chapterNumber.trim() && t != chapterNumber }?.let {
                append(" \u200F- ")
                append(it)
            }
        }
        scanlator = uploader ?: "\u200B"
        chapter_number = chapterNumber.toFloatOrNull() ?: 0f
        date_upload = createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds() ?: 0L
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        SortFilter(),
        YearFilter(),
        StatusFilter(),
    )

    companion object {
        private val SUPPORTED_TYPES = setOf("manga", "manhua", "manhwa")
        private val LANG_SEGMENTS = setOf("ar", "en")
    }
}
