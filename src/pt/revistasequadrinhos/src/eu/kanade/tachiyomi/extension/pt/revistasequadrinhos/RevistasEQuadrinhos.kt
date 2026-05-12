package eu.kanade.tachiyomi.extension.pt.revistasequadrinhos

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class RevistasEQuadrinhos : HttpSource() {

    override val name = "Revistas e Quadrinhos"
    override val baseUrl = "https://revistasequadrinhos.com"
    override val lang = "pt"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("popular-comics")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("ul.videos > li").map { element ->
            val a = element.selectFirst("a.titulo") ?: throw Exception("Manga URL is mandatory")
            val img = element.selectFirst("div.thumb-conteudo img")

            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = img?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst(".paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()

            return GET(url, headers)
        }

        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()

        var path = ""
        if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
            path = "category/${categoryFilter.toUriPart()}"
        } else if (tagFilter != null && tagFilter.toUriPart().isNotEmpty()) {
            path = "tag/${tagFilter.toUriPart()}"
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (path.isNotEmpty()) {
                addPathSegments(path)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".post-conteudo h1")?.text() ?: throw Exception("Manga title is mandatory")
            description = document.select(".post-texto p").joinToString("\n") { it.text() }
            genre = document.select(".post-tags a").joinToString(", ") { it.text() }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")

            // Site treats each post/comic as a single entity, usually completed once uploaded.
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapter = SChapter.create().apply {
            name = "Capítulo Único"
            url = response.request.url.encodedPath

            val dateStr = document.selectFirst("meta[property=article:published_time]")?.attr("content")
            // SimpleDateFormat with 'Z' pattern parses RFC 822 offsets (+0000) but not ISO 8601 (+00:00).
            // Strip the colon from the timezone offset while preserving all digits.
            date_upload = dateStr
                ?.replace(Regex("([-+]\\d{2}):(\\d{2})$"), "$1$2")
                ?.let { dateFormat.tryParse(it) }
                ?: 0L
        }

        return listOf(chapter)
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val gallery = document.select("div.dgwt-jg-gallery figure.dgwt-jg-item a")
        if (gallery.isNotEmpty()) {
            return gallery.mapIndexed { index, a ->
                Page(index, imageUrl = a.attr("abs:href"))
            }
        }

        return document.select(".post-texto img").mapIndexed { index, img ->
            val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-lazy-src") }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        Filter.Header("Nota: Ignorado se usar pesquisa de texto."),
        Filter.Separator(),
        CategoryFilter(),
        Filter.Separator(),
        Filter.Header("Nota: Ignorado se escolher uma categoria."),
        TagFilter(),
    )
}
