package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class Nartag : HttpSource() {

    override val name = "Traducciones Amistosas"

    override val baseUrl = "https://rncalation.online"

    override val lang = "es"

    override val supportsLatest = true

    // Keep versionId stable so existing users' library entries stay linked
    override val versionId = 2

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "views")
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseLibrary(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "latest")
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLibrary(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> if (filter.state != 0) addQueryParameter("type", filter.options[filter.state])
                    is StatusFilter -> if (filter.state != 0) addQueryParameter("status", filter.options[filter.state])
                    is SortFilter -> addQueryParameter("sort", filter.options[filter.state])
                    is GenreFilter -> if (filter.state) addQueryParameter("genre", filter.name)
                    else -> {}
                }
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseLibrary(response)

    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        Filter.Header("Géneros"),
        GenreFilter("Acción"),
        GenreFilter("Aventura"),
        GenreFilter("Comedia"),
        GenreFilter("Drama"),
        GenreFilter("Fantasía"),
        GenreFilter("Fantasia"),
        GenreFilter("Harem"),
        GenreFilter("Love"),
        GenreFilter("Manhua"),
        GenreFilter("Murim"),
        GenreFilter("Reencarnacion"),
        GenreFilter("Romance"),
        GenreFilter("Supernatural"),
        GenreFilter("Sistema"),
        GenreFilter("Cultivación"),
        GenreFilter("+15"),
    )

    // ============================== Parsing ===============================
    private fun parseLibrary(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.comic-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("p.line-clamp-2")?.text()
                    ?: element.selectFirst("p.text-\\[\\.85rem\\]")?.text()
                    ?: ""
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("nav.lib-pagination a.lib-page-btn--nav[href*=page=]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.sm\\:w-56 img")?.imgAttr()
            description = document.selectFirst("p.text-sm")?.text()

            val badges = document.select("span.comic-badge").map { it.text().trim() }
            genre = document.select("a[href*=\"/library?genre=\"]").joinToString { it.text() }

            status = when {
                badges.any { it.contains("En emisión", true) || it.contains("Ongoing", true) } -> SManga.ONGOING
                badges.any { it.contains("Completed", true) || it.contains("Finalizado", true) } -> SManga.COMPLETED
                badges.any { it.contains("Hiatus", true) || it.contains("En pausa", true) } -> SManga.ON_HIATUS
                badges.any { it.contains("Cancelled", true) || it.contains("Cancelado", true) } -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        // Extract manga slug from URL for building premium chapter URLs
        val requestUrl = response.request.url.toString()
        val slug = requestUrl.substringAfterLast("/comics/").substringBefore("/")

        // Chapters are in two places:
        // 1. div#chapter-list a — first ~20 chapters (both free and premium)
        // 2. template#chapters-extra a — remaining chapters (free only, premium not in template)
        val chapterLinks = document.select("div#chapter-list a").toMutableList()
        val template = document.selectFirst("template#chapters-extra")
        if (template != null) {
            chapterLinks.addAll(template.select("a[href*=/cap/]"))
        }

        chapterLinks.forEach { a ->
            val href = a.attr("href")
            if (href.isNotEmpty()) {
                val chapterUrl = when {
                    href.contains("/cap/") -> href // Free chapter: direct link
                    href.contains("/auth/login?redirect=") -> {
                        // Premium chapter: extract chapter number from text and build URL
                        val chapterText = a.selectFirst("span.flex-1")?.text()?.trim() ?: ""
                        val chapterNum = chapterText.substringAfterLast(" ").takeIf { it.all { it.isDigit() } }
                        if (chapterNum != null) "/comics/$slug/cap/$chapterNum" else ""
                    }
                    else -> ""
                }

                if (chapterUrl.isNotEmpty()) {
                    val isPremium = href.contains("/auth/login?redirect=")
                    val chapterName = a.selectFirst("span.flex-1")?.text()?.trim() ?: ""
                    chapters.add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(chapterUrl)
                            name = if (isPremium) "🔒 $chapterName" else chapterName
                            date_upload = parseDate(a.selectFirst("span.text-\\[\\.65rem\\]")?.text())
                        },
                    )
                }
            }
        }

        return chapters
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.page-wrap img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("src") -> attr("abs:src")
        else -> ""
    }.trim()

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            dateFormat.parse(dateStr.trim())?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("M/d/yyyy", Locale.ENGLISH)
    }

    // ============================== Filters ===============================
    class TypeFilter : Filter.Select<String>("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua", "Novel", "Otro"), 0) {
        val options = arrayOf("", "Manga", "Manhwa", "Manhua", "Novel", "Other")
    }

    class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "En curso", "Completado", "En pausa", "Cancelado"), 0) {
        val options = arrayOf("", "Ongoing", "Completed", "Hiatus", "Cancelled")
    }

    class SortFilter : Filter.Select<String>("Ordenar por", arrayOf("Más reciente", "Actualizado", "Más visto", "Mejor valorado", "A-Z"), 0) {
        val options = arrayOf("latest", "updated", "views", "rating", "title")
    }

    class GenreFilter(name: String) : Filter.CheckBox(name, false)
}
