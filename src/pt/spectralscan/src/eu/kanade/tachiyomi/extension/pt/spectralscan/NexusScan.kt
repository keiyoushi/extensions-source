package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NexusScan : HttpSource() {

    // SpectralScan (pt-BR) -> Nexus Scan (pt-BR)
    override val id = 5304928452449566995L

    override val lang = "pt-BR"

    override val name = "Nexus Scan"

    override val baseUrl = "https://nexustoons.site"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .addInterceptor { chain ->
            chain.proceed(chain.request()).also {
                val path = it.request.url.encodedPath
                if (path.startsWith("/accounts/login") || path.startsWith("/login")) {
                    it.close()
                    throw IOException("Faça o login na WebView para acessar o conteúdo")
                }
            }
        }
        .build()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    private val dateNumberRegex = Regex("""\d+""")

    private val ajaxHeaders by lazy {
        headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", "$baseUrl/biblioteca/")
            .build()
    }

    // ==================== AJAX Manga List ==========================

    private fun parseMangaListResponse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val document = Jsoup.parse(result.html, baseUrl)

        val mangas = document.select("a.content-card[href*='/manga/']").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".font-semibold")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, result.has_next)
    }

    // ==================== Popular ==========================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("q", "")
            .addQueryParameter("genre", "")
            .addQueryParameter("type", "")
            .addQueryParameter("view", "grid")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")
            .addQueryParameter("q", "")
            .addQueryParameter("genre", "")
            .addQueryParameter("type", "")
            .addQueryParameter("view", "grid")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var sortValue = "popular"
        var genreValue = ""
        var typeValue = ""

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> {
                    val value = filter.selected()
                    if (value.isNotEmpty()) {
                        when (filter.parameter) {
                            "sort" -> sortValue = value
                            "genre" -> genreValue = value
                            "type" -> typeValue = value
                        }
                    }
                }
                else -> {}
            }
        }

        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sortValue)
            .addQueryParameter("q", query)
            .addQueryParameter("genre", genreValue)
            .addQueryParameter("type", typeValue)
            .addQueryParameter("view", "grid")
            .build()

        return GET(url, ajaxHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Details =======================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.item-title, h1")!!.text()
            thumbnail_url = document.selectFirst("img.item-cover-image")?.absUrl("src")
            description = document.select(".item-description p, .item-description").text()

            genre = document.select(".genre-tag, .hero-genres .genre-tag").joinToString { it.text() }

            document.select(".detail-item").forEach { item ->
                val label = item.selectFirst(".detail-label")?.text()?.lowercase() ?: ""
                val value = item.selectFirst(".detail-value")?.text()
                when {
                    listOf("autor", "author").any { label.contains(it) } -> author = value
                    listOf("artista", "artist").any { label.contains(it) } -> artist = value
                    label.contains("status") -> status = value.parseStatus()
                }
            }
        }
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        listOf("em andamento", "ongoing", "ativo", "lançando").any { contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("completo", "completed", "finalizado").any { contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("pausado", "hiato", "on hiatus").any { contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        listOf("cancelado", "cancelled", "dropped").any { contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ==================== Chapter =======================

    private fun getMangaSlug(url: String) = url.substringAfter("/manga/").trimEnd('/')

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { it.parseAs<ChapterListResponse>() }
            .flatMap { firstPage ->
                val chapters = parseChaptersFromHtml(firstPage.chapters_html)

                if (!firstPage.has_next) {
                    Observable.just(chapters)
                } else {
                    fetchRemainingChapters(getMangaSlug(manga.url), chapters, 2)
                }
            }
    }

    private fun fetchRemainingChapters(
        slug: String,
        accumulatedChapters: List<SChapter>,
        currentPage: Int,
    ): Observable<List<SChapter>> {
        val url = "$baseUrl/ajax/load-chapters/".toHttpUrl().newBuilder()
            .addQueryParameter("item_slug", slug)
            .addQueryParameter("page", currentPage.toString())
            .addQueryParameter("sort", "desc")
            .addQueryParameter("q", "")
            .build()

        return client.newCall(GET(url, ajaxHeaders)).asObservableSuccess()
            .map { it.parseAs<ChapterListResponse>() }
            .flatMap { page ->
                val allChapters = accumulatedChapters + parseChaptersFromHtml(page.chapters_html)

                if (page.has_next) {
                    fetchRemainingChapters(slug, allChapters, currentPage + 1)
                } else {
                    Observable.just(allChapters)
                }
            }
    }

    private fun parseChaptersFromHtml(html: String): List<SChapter> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("a.chapter-item[href]").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".chapter-number")?.text()
                    ?: element.attr("data-chapter-number")?.let { "Capítulo $it" }
                    ?: "Capítulo"
                setUrlWithoutDomain(element.absUrl("href"))

                element.selectFirst(".chapter-date")?.text()?.let { dateStr ->
                    date_upload = parseChapterDate(dateStr)
                }
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        val url = "$baseUrl/ajax/load-chapters/".toHttpUrl().newBuilder()
            .addQueryParameter("item_slug", slug)
            .addQueryParameter("page", "1")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("q", "")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun chapterListParse(response: Response) =
        parseChaptersFromHtml(response.parseAs<ChapterListResponse>().chapters_html)

    private fun parseChapterDate(dateStr: String): Long {
        val value = dateNumberRegex.find(dateStr)?.value?.toIntOrNull() ?: 1
        return when {
            "hoje" in dateStr -> Calendar.getInstance().timeInMillis
            "ontem" in dateStr -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
            "hora" in dateStr -> Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -value) }.timeInMillis
            "dia" in dateStr -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -value) }.timeInMillis
            "semana" in dateStr -> Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -value) }.timeInMillis
            "mês" in dateStr || "mes" in dateStr -> Calendar.getInstance().apply { add(Calendar.MONTH, -value) }.timeInMillis
            else -> dateFormat.tryParse(dateStr)
        }
    }

    // ==================== Page ==========================

    override fun pageListParse(response: Response): List<Page> {
        val pageData = response.asJsoup()
            .selectFirst("script#page-data")?.data()
            ?: return emptyList()

        return pageData.parseAs<List<PageData>>().mapIndexed { index, page ->
            Page(index, imageUrl = page.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    // ==================== Filters ==========================

    override fun getFilterList() = FilterList(
        SelectFilter("Ordenar Por", "sort", sortList),
        SelectFilter("Gênero", "genre", genreList),
        SelectFilter("Tipo", "type", typeList),
    )
}
