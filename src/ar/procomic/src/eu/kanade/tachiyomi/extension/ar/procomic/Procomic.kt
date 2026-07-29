package eu.kanade.tachiyomi.extension.ar.procomic

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Procomic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(
            CookieInterceptor(
                baseUrl.removePrefix("https://"),
                listOf("safe_browsing" to "off", "language" to "ar"),
            ),
        )
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
        val off = if (segs.firstOrNull() in listOf("ar", "en")) 1 else 0
        if (segs.size < off + 4 || segs.getOrNull(off) != "series") return null
        val type = segs[off + 1]
        if (type !in SUPPORTED_TYPES) return null
        val id = segs[off + 2]
        val slug = segs[off + 3]
        return SManga.create().also {
            it.url = "/$id"
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
        val id = manga.url.removePrefix("/")
        val m = manga.memo
        val type = m["type"]!!.jsonPrimitive.content
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter), headersBuilder().set("rsc", "1").build())
        return response.extractNextJs<ChapterImages>()?.appImages?.mapIndexed { i, img ->
            Page(i, imageUrl = img.mobile ?: img.desktop ?: "")
        } ?: emptyList()
    }

    private fun ApiManga.toSManga(): SManga = SManga.create().apply {
        url = "/$id"
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
        url = "/$id"
        memo = buildJsonObject {
            put("type", type)
            put("slug", slug)
        }
        title = this@toSManga.title
        thumbnail_url = (coverImageApp?.desktop ?: coverImage)?.let {
            if (it.startsWith("/")) "$baseUrl$it" else it
        }
    }
    override fun getChapterUrl(chapter: SChapter): String {
        val m = chapter.memo
        return "$baseUrl/series/${m["type"]!!.jsonPrimitive.content}/${m["seriesId"]!!.jsonPrimitive.content}/${m["slug"]!!.jsonPrimitive.content}/${chapter.url.removePrefix("/")}/${m["chapterNumber"]!!.jsonPrimitive.content}"
    }

    private fun ApiChapter.toSChapter(type: String, seriesId: String, slug: String): SChapter = SChapter.create().apply {
        url = "/$id"
        memo = buildJsonObject {
            put("type", type)
            put("seriesId", seriesId)
            put("slug", slug)
            put("chapterNumber", chapterNumber)
        }
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
