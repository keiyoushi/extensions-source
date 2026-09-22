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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    private fun parseMangaCards(document: Document): List<SManga> {
        return document.select("article.lc-card").mapNotNull { element ->
            val titleElement = element.selectFirst("a.lc-card-name")
                ?: return@mapNotNull null

            SManga.create().apply {
                title = titleElement.text()
                thumbnail_url = element.selectFirst("a.lc-card-cover")?.coverUrl()
                setUrlWithoutDomain(titleElement.attr("href"))
            }
        }
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.lc-slider-track div.lc-slide").mapNotNull { element ->
            val titleElement = element.selectFirst("a.lc-slide-name")
                ?: return@mapNotNull null

            SManga.create().apply {
                title = titleElement.text()
                thumbnail_url = element.selectFirst("a.lc-slide-cover")?.coverUrl()
                setUrlWithoutDomain(titleElement.attr("href"))
            }
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("manga")
            .addPathSegment("")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        } else {
            filters.firstInstanceOrNull<GenreFilter>()
                ?.takeIf { it.state != 0 }
                ?.let { urlBuilder.addQueryParameter("genre", it.toUriPart()) }

            filters.firstInstanceOrNull<ThemeFilter>()
                ?.takeIf { it.state != 0 }
                ?.let { urlBuilder.addQueryParameter("theme", it.toUriPart()) }

            filters.firstInstanceOrNull<TypeFilter>()
                ?.takeIf { it.state != 0 }
                ?.let { urlBuilder.addQueryParameter("type", it.toUriPart()) }

            filters.firstInstanceOrNull<SortFilter>()
                ?.takeIf { it.state != 0 }
                ?.let { urlBuilder.addQueryParameter("sort", it.toUriPart()) }

            filters.firstInstanceOrNull<StatusFilter>()
                ?.takeIf { it.state != 0 }
                ?.let { urlBuilder.addQueryParameter("status", it.toUriPart()) }
        }

        urlBuilder.addQueryParameter("page", page.toString())

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(
            mangas = parseMangaCards(document),
            hasNextPage = document.selectFirst("a[rel=next]") != null,
        )
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Los filtros se ignoran al realizar una búsqueda por texto."),
        Filter.Header("Los filtros se pueden combinar."),
        GenreFilter(),
        ThemeFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.lc-release").mapNotNull { element ->
            val titleElement = element.selectFirst("a.lc-release-title")
                ?: return@mapNotNull null

            SManga.create().apply {
                title = titleElement.text()
                thumbnail_url = element.selectFirst("a.lc-release-cover")?.coverUrl()
                setUrlWithoutDomain(titleElement.attr("href"))
            }
        }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val details = document.selectFirst("article.lc-panel")
        val facts = details?.select("ul.lc-facts > li").orEmpty()

        fun fact(name: String): String = facts
            .firstOrNull {
                it.selectFirst("span.k")
                    ?.text()
                    ?.equals(name, ignoreCase = true) == true
            }
            ?.select("a, span")
            ?.lastOrNull()
            ?.text()
            ?.trim()
            .orEmpty()

        val synopsis = document
            .selectFirst("#sinopsis p")
            ?.text()
            ?.trim()
            .orEmpty()
            .takeUnless { it.equals("Esta serie todavia no tiene sinopsis.", ignoreCase = true) }
            .orEmpty()

        val altNames = details
            ?.selectFirst("p.small.lc-muted")
            ?.text()
            ?.trim()
            .orEmpty()

        return SManga.create().apply {
            title = details
                ?.selectFirst("h1")
                ?.text()
                ?.trim()
                .orEmpty()

            author = fact("Autor")

            artist = fact("Dibujo")

            genre = details
                ?.select("a[href*=\"?genre=\"], a[href*=\"?theme=\"]")
                ?.eachText()
                ?.joinToString()
                .orEmpty()

            status = fact("Estado").toStatus()

            description = buildString {
                if (synopsis.isNotBlank()) {
                    append(synopsis)
                }

                if (altNames.isNotBlank()) {
                    if (isNotEmpty()) {
                        append("\n\n")
                    }

                    append("Títulos alternativos: ")
                    append(altNames)
                }
            }

            thumbnail_url = details
                ?.selectFirst(".lc-cover-lg")
                ?.coverUrl()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document
            .select("#chapterList a.lc-chapter-row")
            .mapNotNull { element ->
                val url = element.attr("abs:href")
                val name = element.selectFirst(".n")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                if (url.isBlank() || name.isBlank()) {
                    return@mapNotNull null
                }

                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    this.name = name
                    date_upload = dateFormatter.tryParseDate(
                        element.selectFirst(".d")
                            ?.text()
                            ?.trim(),
                    )
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document
            .select("main img[data-src]")
            .mapNotNull { image ->
                image.attr("abs:data-src")
                    .takeIf { it.isNotBlank() }
            }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private fun Element.coverUrl(): String? {
        val image = selectFirst("img") ?: return null

        return listOf(
            image.attr("abs:data-lazy-src"),
            image.attr("abs:data-src"),
            image.attr("abs:src"),
        ).firstOrNull { url ->
            url.isNotBlank() &&
                !url.startsWith("data:") &&
                !url.startsWith("blob:")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Paused" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
