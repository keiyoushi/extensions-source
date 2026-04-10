package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class KomikuCC : HttpSource() {

    override val name = "Komiku.cc"

    override val baseUrl = "https://komiku.cc"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val rscHeaders = headersBuilder()
        .add("Rsc", "1")
        .build()

    private fun String.toAbsoluteImageUrl(): String = if (startsWith("http")) this else "https://cdn.komiku.cc/$this"

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list?order=popular&page=$page", rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list?order=update&page=$page", rscHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET("$baseUrl/search?q=$query", rscHeaders)
        }

        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
        }.build()

        return GET(url, rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    private fun parseMangaListPage(response: Response): MangasPage {
        val data = response.extractNextJs<List<MangaListDto>>()
            ?: return MangasPage(emptyList(), false)

        val mangas = data.map {
            SManga.create().apply {
                title = it.title
                url = "/komik/${it.link}"
                thumbnail_url = it.img?.toAbsoluteImageUrl()
            }
        }

        val hasNextPage = mangas.size >= 24

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<MangaRscDto>()?.manga
            ?: throw Exception("Failed to parse manga details")

        return SManga.create().apply {
            title = data.title
            author = data.author
            status = parseStatus(data.status)
            genre = data.genres.joinToString { it.title }
            description = data.des
            thumbnail_url = data.img?.toAbsoluteImageUrl()
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<MangaRscDto>()?.manga
            ?: return emptyList()

        return data.chapters.map {
            SChapter.create().apply {
                name = it.title
                url = "/${it.link}"
                date_upload = it.createdAt?.let { dateStr ->
                    dateFormat.tryParse(dateStr)
                } ?: 0L
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PageListDto>()
            ?: throw Exception("Failed to parse page list")

        return data.images.mapIndexed { index, imageUrl ->
            Page(index, "", imageUrl.toAbsoluteImageUrl())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Filter.Header("Search query akan mengabaikan filter di bawah"),
        StatusFilter(),
        TypeFilter(),
        OrderFilter(),
        GenreList(),
    )
}
