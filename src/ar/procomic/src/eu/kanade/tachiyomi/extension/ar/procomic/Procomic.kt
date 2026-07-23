package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

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
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept-Encoding", "gzip") // ponytail: only gzip is reliably handled
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = getFilterList()
        filters.filterIsInstance<SortFilter>().firstOrNull()?.state = 0 // ponytail: "popular" is default
        return searchApi(page, filters)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = getFilterList()
        filters.filterIsInstance<SortFilter>().firstOrNull()?.state = 2 // "latest_chapter"
        return searchApi(page, filters)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchApi(page, filters, query)

    private suspend fun searchApi(page: Int, filters: FilterList, query: String = ""): MangasPage = try {
        val isCatalog = query.isBlank()
        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()
        val yearFilter = filters.filterIsInstance<YearFilter>().firstOrNull()
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
            }
            .build()
        val request = GET(url, headers).newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
        val response = client.newCall(request).await()
        val data = response.parseAs<SearchResponse>()
        MangasPage(
            data.data.filter { it.type in SUPPORTED_TYPES }.map { it.toSManga() },
            data.meta?.hasNextPage() ?: false,
        )
    } catch (_: Exception) {
        MangasPage(emptyList(), false)
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
        return client.newCall(GET(url, headers)).await().parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = client.newCall(GET(getChapterUrl(chapter), headersBuilder().set("rsc", "1").build())).await().body?.string() ?: return emptyList()
        return body.extractNextJsRsc<ChapterImages>()?.appImages?.mapIndexed { i, img ->
            Page(i, imageUrl = img.mobile ?: img.desktop ?: "")
        } ?: emptyList()
    }

    private fun ApiManga.toSManga(): SManga = SManga.create().apply {
        url = "/series/$type/$id/$slug"
        title = title
        metadata?.let { meta ->
            artist = meta.artist
            author = meta.author
            description = buildString {
                description?.let { append(it.trim(), "\n\n") }
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
                meta.origin?.also { add(it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }) }
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
        title = title
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
            append(chapterNumber.toFloatOrNull()?.let { it.toString().substringBefore(".0") } ?: chapterNumber)
            title?.trim()?.takeIf { t -> t.isNotBlank() && t != chapterNumber.trim() && t != chapterNumber }?.let {
                append(" \u200F- ")
                append(it)
            }
        }
        scanlator = uploader ?: "\u200B"
        chapter_number = chapterNumber.toFloatOrNull() ?: 0f
        date_upload = dateFormat.tryParse(createdAt)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        SortFilter(),
        YearFilter(),
    )

    companion object {
        private val SUPPORTED_TYPES = setOf("manga", "manhua", "manhwa")
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
    }
}
