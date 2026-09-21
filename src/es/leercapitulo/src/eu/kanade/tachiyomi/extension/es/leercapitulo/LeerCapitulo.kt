package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(1, 3.seconds) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(".lc-slide").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-slide-name")!!.absUrl("href"))
                title = element.selectFirst("a.lc-slide-name")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("article.lc-release").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-release-title")!!.absUrl("href"))
                title = element.selectFirst("a.lc-release-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            addUriPart("genre", filters.firstInstanceOrNull<GenreFilter>())
            addUriPart("theme", filters.firstInstanceOrNull<ThemeFilter>())
            addUriPart("type", filters.firstInstanceOrNull<TypeFilter>())
            addUriPart("status", filters.firstInstanceOrNull<StatusFilter>())
            addUriPart("sort", filters.firstInstanceOrNull<SortFilter>())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    private fun HttpUrl.Builder.addUriPart(name: String, filter: UriPartFilter?) {
        filter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter(name, it) }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-card-name")!!.absUrl("href"))
                title = element.selectFirst("a.lc-card-name")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.selectFirst("a.page-link[rel=next]") != null)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Los filtros se pueden combinar con la búsqueda por texto."),
        GenreFilter(),
        ThemeFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst(".lc-cover-lg img")?.absUrl("src")

            // The first entry repeats the main title.
            val altNames = document.selectFirst("h1 + p.lc-muted")?.text()
                ?.split(" · ")?.drop(1)?.joinToString(" · ")
            description = buildString {
                document.selectFirst("#sinopsis p")?.wholeText()?.trim()?.takeIf { it.isNotEmpty() }?.let(::append)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alt name(s): ")
                    append(altNames)
                }
            }

            genre = document.select("a.badge[href^='/manga/?genre='], a.badge[href^='/manga/?theme=']")
                .joinToString { it.text() }
            author = document.fact("Autor")
            artist = document.fact("Dibujo")
            status = document.fact("Estado").toStatus()
        }
    }

    private fun Element.fact(label: String): String? = select(".lc-facts li")
        .firstOrNull { it.selectFirst(".k")?.text() == label }
        ?.children()?.last()?.text()

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("a.lc-chapter-row").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".n")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".d")?.text(), ZoneOffset.UTC)
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("#lcPages img[data-src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("data-src"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String?.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Paused" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
