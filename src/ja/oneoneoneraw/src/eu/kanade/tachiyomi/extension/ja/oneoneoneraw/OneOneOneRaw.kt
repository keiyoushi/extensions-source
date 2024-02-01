package eu.kanade.tachiyomi.extension.ja.oneoneoneraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class OneOneOneRaw : HttpSource() {

    override val name = "111raw"

    override val lang = "ja"

    override val baseUrl = "https://111raw.com"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val apiUrl = "https://api.rawz.org/api/manga".toHttpUrl()

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", apiUrl.host)
        add("Origin", baseUrl)
    }.build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(OrderByFilter("views")))

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(OrderByFilter("created_at")))

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.newBuilder().apply {
            addQueryParameter("limit", "36")
            if (query.isNotEmpty()) {
                addEncodedQueryParameter("name", query)
            } else {
                addQueryParameter("order_by", filters.firstInstanceOrNull<OrderByFilter>()?.selectedValue())
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ListDto>()
        val mangaList = data.data.map { it.toSManga() }
        val hasNextPage = data.page.currentPage < data.page.lastPage
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Filters ==============================

    private open class SelectFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    private class OrderByFilter(defaultOrder: String? = null) : SelectFilter(
        "Order by",
        arrayOf(
            Pair("Recently", "updated_at"),
            Pair("New", "created_at"),
            Pair("Hot", "views"),
        ),
        defaultOrder,
    )

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Separator(),
        OrderByFilter(),
    )

    // =========================== Manga Details ============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(manga)

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException()

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("-")
        return GET("$apiUrl/$id/childs", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ChapterListDto>().data.map { it.toSChapter() }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptData = document.selectFirst("script:containsData(initialChapter)")?.data()
            ?: throw Exception("Unable to find image data")
        val data = json.decodeFromString<PagesDto>(scriptData).props.pageProps.initialChapter.images
        return data.map { img ->
            Page(img.index, imageUrl = img.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/avif,image/webp,*/*")
        .build()

    // ============================= Utilities ==============================

    private fun ChapterListDto.ChapterDto.toSChapter(): SChapter = SChapter.create().apply {
        name = this@toSChapter.name
        chapter_number = index.toFloat()
        url = "/read/$slug-$chapterId"
        createdAt?.let { date_upload = parseDate(it) }
    }

    private fun ListDto.EntryDto.toSManga(): SManga = SManga.create().apply {
        title = name
        url = "/manga/$slug-$mangaId"
        thumbnail_url = image
        description = buildString {
            this@toSManga.description?.let {
                append(it)
                altName?.let { append("\n\n") }
            }
            altName?.let { append("Alternative name(s): $it") }
        }
        status = this@toSManga.status.parseStatus()
        genre = taxonomy.getGenres()
        initialized = true
    }

    private fun String?.parseStatus(): Int = when (this) {
        "ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // If no genres are present, "taxonomy" wil be an empty array rather
    // than `TaxonomyDto`
    private fun JsonElement.getGenres(): String? {
        if (this is JsonArray) return null
        return json.decodeFromJsonElement<TaxonomyDto>(this).genres.filter {
            it.type.contains("genre")
        }.joinToString(", ") { it.name }
    }

    private inline fun <reified T> List<*>.firstInstanceOrNull() = firstOrNull { it is T } as? T

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromStream(this.body.byteStream())

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.JAPANESE)
        }
    }
}
