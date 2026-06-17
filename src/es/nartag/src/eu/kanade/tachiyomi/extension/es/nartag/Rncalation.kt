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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class Rncalation : HttpSource() {

    override val name = "Rncalation"

    override val baseUrl = "https://rncalation.online"

    override val lang = "es"

    override val supportsLatest = true

    override val id: Long = 1424373335432920755L

    override val client = network.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.ROOT)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/library?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".lib-grid a.comic-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.select("p.leading-snug").text()
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        val hasNextPage = document.select(".lib-results-page").text().let { text ->
            val parts = text.split("/")
            if (parts.size == 2) {
                val current = parts[0].replace(NON_DIGIT_REGEX, "").toIntOrNull() ?: 1
                val total = parts[1].replace(NON_DIGIT_REGEX, "").toIntOrNull() ?: 1
                current < total
            } else {
                false
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/library?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", sortOptions[filter.state].value)
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("type", filter.values[filter.state])
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("status", filter.values[filter.state])
                        }
                    }
                    is GenreFilter -> {
                        filter.state.forEach { genre ->
                            if (genre.state) {
                                addQueryParameter("genre", genre.name)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".comic-hero-wrap h1").text()
            thumbnail_url = document.select(".comic-hero-wrap img").attr("abs:src")
            description = document.select(".comic-hero-wrap p.leading-relaxed").text()

            author = document.select(".comic-hero-wrap span:contains(Autor)").text().substringAfter("Autor:").ifEmpty { null }
            artist = document.select(".comic-hero-wrap span:contains(Arte)").text().substringAfter("Arte:").ifEmpty { author }

            val badges = document.select(".comic-hero-wrap span.comic-badge").map { it.text().lowercase(Locale.ROOT) }
            status = when {
                badges.any { it.contains("emisión") || it.contains("curso") || it.contains("ongoing") } -> SManga.ONGOING
                badges.any { it.contains("completado") || it.contains("completed") } -> SManga.COMPLETED
                badges.any { it.contains("pausa") || it.contains("hiatus") } -> SManga.ON_HIATUS
                badges.any { it.contains("cancelado") || it.contains("cancelled") } -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            genre = document.select(".comic-hero-wrap a[href*=genre]").joinToString { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select("#chapter-list a").forEach { element ->
            chapters.add(chapterFromElement(element))
        }

        document.select("template#chapters-extra").firstOrNull()?.let { template ->
            val extraHtml = template.html()
            val extraDoc = org.jsoup.Jsoup.parseBodyFragment(extraHtml, response.request.url.toString())
            extraDoc.select("a").forEach { element ->
                chapters.add(chapterFromElement(element))
            }
        }

        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.select("span.flex-1").text()

        val dateText = element.select("span").firstOrNull {
            it.text().contains("/") && it.text().length in 8..10
        }?.text()
        date_upload = dateFormat.tryParse(dateText)

        scanlator = element.select("span").firstOrNull {
            val text = it.text().lowercase()
            text.isNotEmpty() &&
                !it.hasClass("flex-1") &&
                !text.contains("/") &&
                text != "gratis" &&
                text != "nuevo"
        }?.text()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.page-img, .page-wrap img").mapIndexed { index, element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(genresList.map { Genre(it) }),
    )

    private class TypeFilter : Filter.Select<String>("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua", "Novel", "Other"))

    private class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "Ongoing", "Completed", "Hiatus", "Cancelled"))

    private class SortFilter : Filter.Select<SortOption>("Ordenar por", sortOptions)

    private class SortOption(val name: String, val value: String) {
        override fun toString(): String = name
    }

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)
    private class Genre(name: String) : Filter.CheckBox(name)

    companion object {
        private val sortOptions = arrayOf(
            SortOption("Más reciente", "latest"),
            SortOption("Actualizado", "updated"),
            SortOption("Más visto", "views"),
            SortOption("Mejor valorado", "rating"),
            SortOption("A-Z", "title"),
        )

        private val genresList = listOf(
            "Acción",
            "Adventure",
            "Aventura",
            "ciencia ficción",
            "Comedia",
            "Cultivación",
            "Drama",
            "Fantasia",
            "Fantasía",
            "Harem",
            "Love",
            "Manhua",
            "Reencarnacion",
            "Romance",
            "Sistema",
            "Supernatural",
            "+15",
        )

        private val NON_DIGIT_REGEX = Regex("[^0-9]")
    }
}
