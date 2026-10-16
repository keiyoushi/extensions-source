package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.reflect.KMutableProperty0

@Source
abstract class Procomic :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(::interceptImage)
        // Sensitive series render the reader only with the site's safe browsing switched off.
        .addCookie("safe_browsing" to "off")
        .addCookie({ MIRROR_HOST }, "safe_browsing" to "off")

    private val siteHost get() = baseUrl.toHttpUrl().host

    private var popularPage = 1
    private var latestPage = 1
    private var searchPage = 1

    override suspend fun getPopularManga(page: Int): MangasPage = listing(page, ::popularPage) {
        addPathSegments("api/public/content")
        addQueryParameter("sort", "popular")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listing(page, ::latestPage) {
        addPathSegments("api/public/content")
        addQueryParameter("sort", "latest_chapter")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isBlank()) {
        listing(page, ::searchPage) {
            addPathSegments("api/public/content")
            addQueryParameter("sort", filters.firstInstance<SortFilter>().selected)
            filters.firstInstance<TypeFilter>().selected?.let { addQueryParameter("type", it) }
            filters.firstInstance<StatusFilter>().selected?.let { addQueryParameter("progress", it) }
            filters.firstInstance<YearFilter>().selected?.let { addQueryParameter("year", it) }
        }
    } else {
        listing(page, ::searchPage) {
            addPathSegments("api/public/series/search")
            addQueryParameter("search", query)
        }
    }

    // Skips API pages with nothing readable, so the app's page number and the API's drift apart.
    private suspend fun listing(page: Int, cursor: KMutableProperty0<Int>, url: HttpUrl.Builder.() -> Unit): MangasPage {
        if (page == 1) cursor.set(1)
        while (true) {
            val data = client.get(
                baseUrl.toHttpUrl().newBuilder()
                    .apply(url)
                    .addQueryParameter("limit", LIST_LIMIT.toString())
                    .addQueryParameter("page", cursor.get().toString())
                    .build(),
            ).parseAs<ContentPageDto>()
            cursor.set(cursor.get() + 1)
            val mangas = data.data.filter { it.isReadable }.map { it.toSManga(siteHost) }
            if (mangas.isNotEmpty() || !data.hasNextPage) return MangasPage(mangas, data.hasNextPage)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteHost && url.host != MIRROR_HOST) return null
        val segments = url.pathSegments.filterNot { it in LOCALES }
        val id = segments.lastOrNull()?.substringAfterLast('-')?.toIntOrNull() ?: return null
        val seriesId = if (segments.first() == "chapter") {
            apiOrNull("chapters", id)?.parseAs<ChapterRefDto>()?.contentId ?: return null
        } else {
            id
        }
        val series = apiOrNull("content", seriesId)?.parseAs<ContentDto>() ?: return null
        return series.takeIf { it.isReadable }?.toDetails(siteHost)
    }

    private suspend fun apiOrNull(path: String, id: Int): Response? {
        val response = client.get(apiUrl(path, id), ensureSuccess = false)
        if (response.code != 404) return response
        response.close()
        return null
    }

    private fun apiUrl(path: String, id: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("api/public")
        .addPathSegment(path)
        .addPathSegment(id.toString())
        .build()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) {
            async { client.get(apiUrl("content", manga.url.toInt())).parseAs<ContentDto>().toDetails(siteHost) }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { getChapterList(manga) } else null
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/public")
            .addPathSegment(manga.memo["type"]!!.string)
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .addQueryParameter("limit", CHAPTER_LIMIT.toString())
            .addQueryParameter("order", "desc")
            .build()

        val first = client.get(url.newBuilder().addQueryParameter("page", "1").build())
            .parseAs<ChapterPageDto>()
        val pages = (first.total + CHAPTER_LIMIT - 1) / CHAPTER_LIMIT
        val rest = coroutineScope {
            (2..pages).map { page ->
                async {
                    client.get(url.newBuilder().addQueryParameter("page", page.toString()).build())
                        .parseAs<ChapterPageDto>()
                }
            }.awaitAll()
        }

        val slug = manga.memo["slug"]!!.string
        val showLocked = preferences.getBoolean(SHOW_LOCKED_KEY, false)
        return (listOf(first) + rest).asSequence()
            .flatMap { it.data.asSequence() }
            .filter { it.isArabic }
            .filter { showLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
            .toList()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(lang)
        .addPathSegment("${manga.memo["slug"]!!.string}-${manga.url}")
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(lang)
        .addPathSegment("chapter")
        .addPathSegment("${chapter.memo["slug"]!!.string}-${chapter.memo["number"]!!.string}-${chapter.url}")
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = readerProps(getChapterUrl(chapter)) ?: return emptyList()
        val deferred = reader.deferredMedia?.let { deferredMedia(reader.chapterId, it.token) }

        val images = reader.appImages.map { it.mobile ?: it.desktop!! } +
            deferred?.images.orEmpty().map { signedImageUrl(it) }
        val maps = deferred?.maps.orEmpty().mapIndexed { index, entry ->
            val map = MapRequestDto(
                reader.chapterId,
                MapPlanBodyDto(entry.token, entry.method, reader.cdnPath, images.size + index),
            )
            HttpUrl.Builder().scheme("http").host(PIECE_HOST).fragment(map.toJsonString()).build().toString()
        }

        return (images + maps).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private suspend fun readerProps(chapterUrl: String): ReaderDto? {
        val document = client.get(chapterUrl).asJsoup()
        document.extractNextJs<ReaderDto>(::isReaderProps)?.let { return it }
        // Series the site moved to its other domain render a bounce page instead.
        val moved = document.extractNextJs<MovedDto>() ?: return null
        return client.get(moved.targetUrl).asJsoup().extractNextJs<ReaderDto>(::isReaderProps)
    }

    private fun isReaderProps(element: JsonElement) = element is JsonObject && "chapterId" in element && "appImages" in element

    private suspend fun deferredMedia(chapterId: Int, token: String): DeferredDataDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapter-deferred-media")
            .addPathSegment(chapterId.toString())
            .addQueryParameter("token", token)
            .build()
        return client.get(url).parseAs<DeferredPageDto>().data
    }

    private fun signedImageUrl(image: String) = HttpUrl.Builder()
        .scheme("http")
        .host(SIGNED_IMAGE_HOST)
        .addQueryParameter("url", image)
        .build()
        .toString()

    private fun interceptImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        return when {
            url.host == PIECE_HOST -> {
                val buffer = runBlocking { stitch(url, chain.call()) }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
                    .build()
            }
            url.host == SIGNED_IMAGE_HOST -> {
                // The site signs each image url right before loading it; signatures last minutes.
                val image = url.queryParameter("url")!!
                val signed = runBlocking { signImage(image) }
                val signedUrl = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments(CDN_IMAGE_PATH)
                    .addQueryParameter("url", image)
                    .addQueryParameter("token", signed.token)
                    .addQueryParameter("expires", signed.expires.toString())
                    .build()
                chain.proceed(request.newBuilder().url(signedUrl).build())
            }
            else -> chain.proceed(request)
        }
    }

    private suspend fun signImage(image: String): SignedImageDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("$CDN_IMAGE_PATH/sign")
            .build()
        return client.post(url, SignRequestDto(image).toJsonRequestBody()).parseAs<SignedImageDto>()
    }

    private suspend fun mapPlan(map: MapRequestDto): MapPlanDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapter-map-proxy-plan")
            .addPathSegment(map.chapterId.toString())
            .build()
        return client.post(url, map.plan.toJsonRequestBody()).parseAs<MapPlanResponseDto>().data.map
    }

    private suspend fun stitch(url: HttpUrl, call: Call): Buffer {
        val plan = mapPlan(url.fragment!!.parseAs<MapRequestDto>())

        val page = Bitmap.createBitmap(plan.dim[0], plan.dim[1], Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        try {
            plan.rects.forEachIndexed { index, rect ->
                // The reader cancels pages it scrolled past; stop fetching their pieces.
                if (call.isCanceled()) throw IOException("Canceled")
                val piece = ImageDecoder.decode(client.get(plan.pieces[plan.order[index]]).body.bytes())
                val destination = Rect(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height)
                canvas.drawBitmap(piece, null, destination, null)
                piece.recycle()
            }
            return Buffer().apply { page.compress(Bitmap.CompressFormat.JPEG, 90, outputStream()) }
        } finally {
            page.recycle()
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        SortFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_KEY
            title = "إظهار الفصول المقفلة"
            summary = "الفصول التي تحتاج عملات أو رابط مختصر أو اشتراك لفتحها على الموقع"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val LIST_LIMIT = 50
        private const val CHAPTER_LIMIT = 100
        private const val MIRROR_HOST = "procomic.net"
        private val LOCALES = setOf("ar", "en")
        private const val CDN_IMAGE_PATH = "api/cdn-image"
        private const val PIECE_HOST = "procomic.pieces"
        private const val SIGNED_IMAGE_HOST = "procomic.signed"
        private const val SHOW_LOCKED_KEY = "showLockedChapters"
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
