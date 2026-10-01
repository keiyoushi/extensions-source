package eu.kanade.tachiyomi.extension.id.komiknextgonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class KomikNextGOnline : KeiSource() {

    override val supportsLatest = false

    // ======================== Popular ========================
    // The redesigned home page no longer lists comics, the comic archive only has covers
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/comic/page/$page/").asJsoup()

        val mangas = document.select("span.comic-thumbnail-wrapper").map { element ->
            SManga.create().apply {
                val image = element.selectFirst("img")!!
                title = image.attr("alt").replace(COVER_SUFFIX_REGEX, "")
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                thumbnail_url = image.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst(".nav-previous a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst(".comic-title, .entry-title")!!
        title = titleElement.text().replace(TITLE_PREFIX_REGEX, "")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // ======================== Latest ========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ======================== Search ========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()
        } else {
            val filter = filters.firstInstanceOrNull<UriPartFilter>()
            val filterUrl = if (filter != null && filter.state != 0) {
                "$baseUrl/${filter.toUriPart()}".toHttpUrl().newBuilder()
            } else {
                baseUrl.toHttpUrl().newBuilder()
            }

            filterUrl.apply {
                if (page > 1) {
                    addQueryParameter("comics_paged", page.toString())
                }
            }.build()
        }

        val document = client.get(url).asJsoup()

        val mangas = document.select("#left-content ul#comic-list li.comic, #left-content article.comic").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details ========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            title = document.selectFirst("meta[property=\"og:title\"]")!!.attr("content").substringBefore(" - ")
            description = document.selectFirst("meta[property=\"og:description\"]")?.attr("content")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.attr("abs:content")
        }

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.selectFirst("meta[property=\"og:url\"]")!!.attr("abs:content"))
            name = "Chapter 1"
            date_upload = Instant.tryParse(
                document.selectFirst("meta[property=\"article:published_time\"]")?.attr("content"),
            )
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    // ======================== Pages ========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("div#spliced-comic img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filter akan diabaikan jika ada pencarian teks"),
        CategoryFilter(),
    )

    companion object {
        private val TITLE_PREFIX_REGEX = Regex("""^#\d+\.\s*""")
        private val COVER_SUFFIX_REGEX = Regex("""\s+cover$""", RegexOption.IGNORE_CASE)
    }
}
