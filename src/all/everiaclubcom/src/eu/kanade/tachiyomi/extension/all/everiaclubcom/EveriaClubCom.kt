package eu.kanade.tachiyomi.extension.all.everiaclubcom

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class EveriaClubCom() : HttpSource() {
    override val baseUrl = "https://www.everiaclub.com"
    override val lang = "all"
    override val name = "EveriaClub (unoriginal)"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val Element.imgSrc: String?
        get() = when {
            hasAttr("data-original") -> attr("data-original")
            hasAttr("data-lazy-src") -> attr("data-lazy-src")
            hasAttr("data-src") -> attr("data-src")
            hasAttr("src") -> attr("src")
            else -> null
        }

    private fun mangaFromElement(it: Element) = SManga.create().apply {
        setUrlWithoutDomain(it.attr("abs:href").removePrefix(baseUrl))
        with(it.selectFirst("img")!!) {
            thumbnail_url = imgSrc
            title = attr("title")
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mainleft .leftp > a").map {
            mangaFromElement(it)
        }
        val isLastPage = document.selectFirst("li:has(span.current) + li > a")
        return MangasPage(mangas, isLastPage != null)
    }

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mainright li a").map {
            mangaFromElement(it)
        }
        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.filterIsInstance<TagFilter>().first()
        val categoryFilter = filters.filterIsInstance<CategoryFilter>().first()
        val url = when {
            tagFilter.state.isNotBlank() -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("tags")
                .addPathSegment(tagFilter.state)
                .addPathSegment(page.toString())
            categoryFilter.state != 0 -> "$baseUrl/${categoryFilter.toUriPart()}?page=$page".toHttpUrl().newBuilder()
            query.isNotBlank() -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment("")
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
            else -> "$baseUrl/?page=$page".toHttpUrl().newBuilder()
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            genre = document.select("div.end span:contains(Tags:) ~ a > p.tags").joinToString {
                it.ownText()
            }
            status = SManga.COMPLETED
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            chapter_number = 1f
            date_upload = 0L
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select(".mainleft img")
        return images.mapIndexed { index, image ->
            Page(index, imageUrl = image.imgSrc)
        }
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        TagFilter(),
        CategoryFilter(),
    )

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Gravure", "Gravure.html"),
            Pair("Japan", "Japan.html"),
            Pair("Korea", "Korea.html"),
            Pair("Thailand", "Thailand.html"),
            Pair("Chinese", "Chinese.html"),
            Pair("Cosplay", "Cosplay.html"),
        ),
    )

    class TagFilter : Filter.Text("Tag")
}
