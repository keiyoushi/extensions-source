package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Procomic : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(
            CookieInterceptor(
                baseUrl.removePrefix("https://"),
                listOf("safe_browsing" to "off", "language" to "ar"),
            ),
        )
        addNetworkInterceptor(
            Interceptor { chain ->
                val response = chain.proceed(chain.request())
                val contentType = response.header("Content-Type") ?: return@Interceptor response
                if (contentType != "image/avif") return@Interceptor response
                avifToJpeg(response)
            },
        )
    }

    override fun Headers.Builder.configureHeaders() = apply {
    }

    private fun avifToJpeg(response: Response): Response {
        val bytes = response.body.bytes()
        val decoder = ImageDecoder.newInstance(ByteArrayInputStream(bytes), false, null) ?: return response
        val bitmap = decoder.decode(Rect(0, 0, decoder.width, decoder.height), 1) ?: run {
            decoder.recycle()
            return response
        }
        decoder.recycle()
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        return response.newBuilder()
            .body(output.toByteArray().toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

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
        val data = client.get(url, cacheControl = CacheControl.FORCE_NETWORK).parseAs<SearchResponse>()
        return MangasPage(
            data.data.filter { it.type in SUPPORTED_TYPES }.map { it.toSManga() },
            data.meta?.let { it.totalPages != null && it.currentPage != null && it.totalPages > it.currentPage } ?: false,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segs = url.pathSegments
        val off = if (segs.firstOrNull() in listOf("ar", "en")) 1 else 0
        if (segs.size < off + 4 || segs.getOrNull(off) != "series") return null
        val type = segs[off + 1]
        if (type !in SUPPORTED_TYPES) return null
        val id = segs[off + 2]
        val slug = segs[off + 3]
        return SManga.create().also {
            it.url = "/series/$type/$id/$slug"
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = manga.url.removePrefix("/series/").split("/")
        val apiManga = fetchApiManga(path[0], path[1])
        val smanga = if (fetchDetails) apiManga.toSManga() else manga
        val chapterList = if (fetchChapters) {
            (apiManga.chapters ?: emptyList())
                .filter { it.language == "AR" }
                .map { it.toSChapter(path[0], path[1], apiManga.slug) }
        } else {
            chapters
        }
        return SMangaUpdate(smanga, chapterList)
    }

    private suspend fun fetchApiManga(type: String, id: String): ApiManga {
        val url = "$baseUrl/api/public/$type/$id"
        return client.get(url).parseAs<ApiManga>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter), headersBuilder().set("rsc", "1").build())
        return response.extractNextJs<ChapterImages>()?.appImages?.mapIndexed { i, img ->
            Page(i, imageUrl = img.mobile ?: img.desktop ?: "")
        } ?: emptyList()
    }

    private fun ApiManga.toSManga(): SManga = SManga.create().apply {
        url = "/series/$type/$id/$slug"
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
        url = "/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = (coverImageApp?.desktop ?: coverImage)?.let {
            if (it.startsWith("/")) "$baseUrl$it" else it
        }
    }

    private fun ApiChapter.toSChapter(type: String, seriesId: String, slug: String): SChapter = SChapter.create().apply {
        url = "/series/$type/$seriesId/$slug/$id/$chapterNumber"
        name = buildString {
            append("\u200F")
            if (coins != null && coins > 0) append("🔒 ")
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
    }
}
