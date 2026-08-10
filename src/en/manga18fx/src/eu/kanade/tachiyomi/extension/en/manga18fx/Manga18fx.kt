package eu.kanade.tachiyomi.extension.en.manga18fx

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import java.time.format.DateTimeFormatter
import java.util.Locale

// The site isn't actually based on Madara but reproduces it very well
@Source
abstract class Manga18fx : Madara() {
    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)

    override val sendViewCount = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val block = document.selectFirst(Evaluator.Class("trending-block"))!!
        val mangas = block.select(Evaluator.Tag("a")).map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        url = element.attr("href")
        title = element.attr("title")
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.attr("data-src")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseLatest(client.get("$baseUrl/page/$page").asJsoup())

    private fun parseLatest(document: Document): MangasPage {
        val mangas = document.select(Evaluator.Class("bsx-item")).map {
            mangaFromElement(it.selectFirst(Evaluator.Tag("a"))!!)
        }
        val nextButton = document.selectFirst(Evaluator.Class("next"))
        val hasNextPage = nextButton != null && nextButton.hasClass("disabled").not()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isEmpty()) {
            filters.forEach { filter ->
                if (filter is GenreFilter) {
                    return parseLatest(client.get(filter.vals[filter.state].url).asJsoup())
                }
            }
            return getLatestUpdates(page)
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return parseLatest(client.get(url).asJsoup())
    }

    // Site has no Madara post ID
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(baseUrl.toHttpUrl().resolve(manga.url)!!).asJsoup()
        return SMangaUpdate(
            if (fetchDetails) parseDetails(document, "", manga.url) else manga,
            if (fetchChapters) fetchChapters(manga.url, "", document) else chapters,
        )
    }

    override val mangaDetailsSelectorDescription = ".dsct"

    override fun chapterListSelector() = ".row-content-chapter > *"

    override val chapterDateSelector = "span.chapter-time"

    class GenreFilter(val vals: List<Genre>) : Filter.Select<String>("Genre", vals.map { it.name }.toTypedArray())

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return document.select(".header-bottom li a").map {
            val href = it.attr("href")
            val url = if (href.startsWith("http")) href else "$baseUrl/$href"

            Genre(it.text(), url)
        }.toJsonElement()
    }

    @Serializable
    class Genre(val name: String, val url: String)

    private var hardCodedTypes: List<Genre> = listOf(
        Genre("Manhwa", "$baseUrl/manga-genre/manhwa"),
        Genre("Manhua", "$baseUrl/manga-genre/manhua"),
        Genre("Raw", "$baseUrl/manga-genre/raw"),
    )

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>()

        val filters = buildList(2) {
            add(Filter.Header("Filters are ignored for text search!"))

            if (!genres.isNullOrEmpty()) {
                add(
                    GenreFilter(hardCodedTypes + genres),
                )
            } else {
                add(
                    Filter.Header("Wait for mangas to load then tap Reset"),
                )
            }
        }

        return FilterList(filters)
    }
}
