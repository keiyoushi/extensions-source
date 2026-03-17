package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDenizi : HttpSource() {
    override val name = "MangaDenizi"
    override val baseUrl = "https://www.mangadenizi.net"
    override val lang = "tr"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json = Json { ignoreUnknownKeys = true }

    private fun pageProps(response: Response): PageProps {
        val document = response.asJsoup()
        val raw = document.selectFirst("div#app")!!.attr("data-page")
        return json.decodeFromString<InertiaPage>(raw).props
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val props = pageProps(response)
        val mangas = props.manga!!.data.map { it.toSManga() }
        val hasNextPage = props.manga.next_page_url != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        popularMangaParse(response)

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = pageProps(response).manga!!.data.firstOrNull()
            ?: pageProps(response).singleManga!!
        return manga.toSManga()
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val props = pageProps(response)
        val manga = props.singleManga ?: props.manga?.data?.firstOrNull() ?: return emptyList()
        return manga.chapters.map { it.toSChapter(manga.slug) }.reversed()
    }

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = pageProps(response).pages ?: return emptyList()
        return pages.mapIndexed { idx, page ->
            Page(idx, imageUrl = page.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    // =========================== Serialization ============================

    private fun MangaItem.toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@toSManga.title
        thumbnail_url = cover_url
        description = this@toSManga.description
        author = authors.firstOrNull()?.name
        genre = categories.joinToString { it.name }
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun ChapterItem.toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug"
        name = buildString {
            append("Bölüm $number")
            if (!title.isNullOrBlank()) append(": $title")
        }
        date_upload = runCatching {
            dateFormat.parse(published_at.substringBefore("T"))?.time ?: 0L
        }.getOrDefault(0L)
        chapter_number = number.toFloat()
    }

    companion object {
        val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }

    // =========================== Data Classes =============================

    @Serializable
    data class InertiaPage(val props: PageProps)

    @Serializable
    data class PageProps(
        val manga: MangaPaginated? = null,
        val pages: List<PageItem>? = null,
    ) {
        // Manga detay sayfasında manga tek obje olarak gelir
        val singleManga: MangaItem? get() = manga?.data?.firstOrNull()
    }

    @Serializable
    data class MangaPaginated(
        val data: List<MangaItem> = emptyList(),
        val next_page_url: String? = null,
    )

    @Serializable
    data class MangaItem(
        val id: Int,
        val title: String,
        val slug: String,
        val description: String? = null,
        val cover_url: String? = null,
        val status: String? = null,
        val authors: List<AuthorItem> = emptyList(),
        val categories: List<CategoryItem> = emptyList(),
        val chapters: List<ChapterItem> = emptyList(),
    )

    @Serializable
    data class AuthorItem(val name: String)

    @Serializable
    data class CategoryItem(val name: String)

    @Serializable
    data class ChapterItem(
        val id: Int,
        val number: Double,
        val title: String? = null,
        val slug: String,
        val published_at: String,
    )

    @Serializable
    data class PageItem(
        val page_number: Int,
        val image_url: String,
    )
}
