package eu.kanade.tachiyomi.extension.en.eggporncomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class Eggporncomics : HttpSource() {

    override val name = "Eggporncomics"

    override val baseUrl = "https://eggporncomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // couldn't find a page with popular comics, defaulting to the popular "anime-comics" category
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/1/anime-comics?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.preview:has(div.name)").map { element ->
            SManga.create().apply {
                element.selectFirst("a:has(img)")?.let { a ->
                    setUrlWithoutDomain(a.absUrl("href"))
                    title = a.text()
                    thumbnail_url = a.selectFirst("img")?.absUrl("src")
                }
            }
        }
        val hasNextPage = document.selectFirst("ul.ne-pe li.next:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-comics?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) {
                    // when combining a category filter and comics filter, if there are no results the source
                    // issues a 404, override that so as not to confuse users
                    if (response.request.url.toString().contains("category-tag") && response.code == 404) {
                        response.close()
                        return@map MangasPage(emptyList(), false)
                    }
                    response.close()
                    throw Exception("HTTP error ${response.code}")
                }
                searchMangaParse(response)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/search/${query.replace(queryRegex, "-")}?page=$page", headers)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
        val filterList = filters.ifEmpty { getFilterList() }
        val category = filterList.firstInstanceOrNull<CategoryFilter>()
        val comics = filterList.firstInstanceOrNull<ComicsFilter>()

        when {
            category?.isNotNull() == true && comics?.isNotNull() == true -> {
                url.addPathSegments("category-tag/${category.toUriPart()}/${comics.toUriPart()}")
            }
            category?.isNotNull() == true -> {
                url.addPathSegments("category/${category.toUriPart()}")
            }
            comics?.isNotNull() == true -> {
                url.addPathSegments("comics-tag/${comics.toUriPart()}")
            }
        }

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        thumbnail_url = document.selectFirst("div.grid div.image img")?.toFullSizeImage()
        description = document.select("div.links ul").joinToString("\n") { element ->
            element.select("a").joinToString(
                prefix = element.select("span").text().replace(descriptionPrefixRegex, ": "),
            ) { it.text() }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            name = "Chapter"
            val document = response.asJsoup()
            date_upload = document.selectFirst("div.info > div.meta li:contains(days ago)")
                ?.let {
                    val days = it.text().substringBefore(" ").toIntOrNull() ?: 0
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
                }
                ?: 0L
        },
    )

    // Pages

    private fun Element.toFullSizeImage() = absUrl("src").replace("thumb300_", "")

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.grid div.image img").mapIndexed { i, img ->
            Page(i, imageUrl = img.toFullSizeImage())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Leave query blank to use filters"),
        Filter.Separator(),
        CategoryFilter("Category", getCategoryList),
        ComicsFilter("Comics", getComicsList),
    )

    companion object {
        private val queryRegex = Regex("""[\s']""")
        private val descriptionPrefixRegex = Regex(""":.*""")
    }
}
