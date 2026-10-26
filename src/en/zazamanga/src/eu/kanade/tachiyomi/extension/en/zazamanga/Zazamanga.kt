package eu.kanade.tachiyomi.extension.en.zazamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.math.roundToInt

@Source
abstract class Zazamanga : MadaraNoAjax() {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val mangaDetailsSelectorGenre = "div.post-content div.tags a"
    override val cancelledStatus = super.cancelledStatus + "drop"

    override val statusFilterOptions = listOf(
        intl["status_filter_ongoing"] to "ongoing",
        intl["status_filter_completed"] to "completed",
        intl["status_filter_canceled"] to "drop",
    )

    // Image hosts answer 503 to requests carrying Origin
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")

    override fun nextPageSelector() = "ul.pagination > li:last-child:not(.disabled) > a[href]"

    override suspend fun archivePage(page: Int, order: String, path: String, query: String): MangasPage {
        val url = baseUrl.toHttpUrl().resolve(path.trimEnd('/'))!!.newBuilder().apply {
            if (order.isNotBlank()) addQueryParameter(orderQueryParameter, order)
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return listPage(url, page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrl().newBuilder().apply {
            setQueryParameter(searchQueryParameter, query.trim())
            filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return listPage(url, page)
    }

    // Chapter links open their series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.takeIf { it.size > 2 && it[0] == mangaSubString }?.get(1)
            ?: return super.getMangaByUrl(url)
        val seriesUrl = url.newBuilder().encodedPath("/").addPathSegment(mangaSubString).addPathSegment(slug).query(null).build()
        return super.getMangaByUrl(seriesUrl)
    }

    override fun Document.mangaId(): String? = selectFirst("#comment-wrapper[data-model=comics]")?.attrOrNull("data-id")

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val synopsis = document.select("div.summary__content h2:contains(Highlights) ~ p").joinToString("\n\n") { paragraph ->
            paragraph.toMarkdown().lines().joinToString("\n", transform = String::trim).trim()
        }
        val ratingCount = document.selectFirst("#countrate")?.text()?.toIntOrNull()?.takeIf { it > 0 }
        val ratingLine = document.selectFirst("#averagerate")?.text()?.toDoubleOrNull()?.takeIf { ratingCount != null }?.let { rating ->
            val stars = rating.roundToInt().coerceIn(0, 5)
            "${"★".repeat(stars)}${"☆".repeat(5 - stars)} $rating ($ratingCount ratings)"
        }
        val altNames = document.selectFirst(altNameSelector)?.ownText()?.takeIf { it.isNotEmpty() && !isUpdating(it) }
        description = listOfNotNull(
            ratingLine,
            document.selectFirst("div.summary-content-release a")?.text()?.let { "**Year:** $it" },
            synopsis.ifEmpty { null },
            altNames?.let { "**${intl["alt_names_heading"]}** $it" },
        ).joinToString("\n\n").ifEmpty { null }
    }

    override fun chapterListSelector() = "div.wp-manga-chapter"

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("data-src")
        element.hasAttr("data-lazy-src") -> element.attr("data-lazy-src")
        element.hasAttr("srcset") -> element.attr("srcset").getSrcSetImage()
        element.hasAttr("data-cfsrc") -> element.attr("data-cfsrc")
        else -> element.attr("src")
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(searchUrl()).asJsoup()
        .select("form.search-advanced-form input[name=genre[]]")
        .map { GenreOption(it.nextElementSibling()!!.text(), it.attr("value")) }
        .distinctBy(GenreOption::slug)
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(
            buildList {
                add(SelectFilter(intl["order_by_filter_title"], "orderby", orderByFilterOptions))
                add(CheckBoxGroup(intl["status_filter_title"], "status[]", statusFilterOptions))
                add(SelectFilter(intl["adult_content_filter_title"], "adult", adultFilterOptions))
                add(TextFilter(intl["author_filter_title"], "author"))
                add(TextFilter(intl["artist_filter_title"], "artist"))
                add(TextFilter(intl["year_filter_title"], "release"))
                if (genres.isNotEmpty()) {
                    add(SelectFilter(intl["genre_condition_filter_title"], "op", genreConditionFilterOptions))
                    add(CheckBoxGroup(intl["genre_filter_title"], "genre[]", genres.map { it.name to it.slug }))
                }
            },
        )
    }

    private fun searchUrl() = baseUrl.toHttpUrl().newBuilder()
        .addQueryParameter(searchQueryParameter, "")
        .addQueryParameter("post_type", "wp-manga")
        .build()

    // Past page 100 the site repeats page 100
    private suspend fun listPage(url: HttpUrl, page: Int): MangasPage {
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), page < LAST_PAGE && document.selectFirst(nextPageSelector()) != null)
    }

    private fun Element.toMarkdown(): String = buildString {
        childNodes().forEach { node ->
            when (node) {
                is TextNode -> append(node.text())
                is Element -> when (node.normalName()) {
                    "br" -> append('\n')
                    "a" -> append("[${node.text()}](${node.absUrl("href")})")
                    "strong", "b" -> append("**${node.toMarkdown()}**")
                    else -> append(node.toMarkdown())
                }
            }
        }
    }

    companion object {
        private const val LAST_PAGE = 100
    }
}
