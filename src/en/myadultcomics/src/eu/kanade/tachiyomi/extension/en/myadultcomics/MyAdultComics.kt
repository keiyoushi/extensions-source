package eu.kanade.tachiyomi.extension.en.myadultcomics

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
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class MyAdultComics : HttpSource() {

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/index.php?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("td.list_container").mapNotNull { element ->
            val link = element.selectFirst("p.text_container > a") ?: return@mapNotNull null
            val href = link.absUrl("href")

            if (href.isEmpty() || link.text().isEmpty()) {
                return@mapNotNull null
            }

            val image = element.selectFirst("img.fon_pic_img")

            SManga.create().apply {
                setUrlWithoutDomain(href)
                title = link.text()
                thumbnail_url = image?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("td.tbl_page a:contains(>>)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun getFilterList() = FilterList(
        Filter.Header("Use the text search field along with the type below."),
        Filters(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchType = filters.firstInstanceOrNull<Filters>()?.selectedValue() ?: "title"

        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("index.php")

        if (searchType == "title") {
            if (query.isNotBlank()) {
                url.addQueryParameter("name", query)
            }
            url.addQueryParameter("search", "yes")
        } else {
            url.addQueryParameter("search", query)
            url.addQueryParameter("sort", searchType)
        }
        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1#TOP")!!.text()
            genre = document.select("p.text_info_book:contains(Tags:) a").joinToString { it.text() }
            artist = document.select("p.text_info_book:contains(Artists:) a").joinToString { it.text() }
            author = artist
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            name = "Gallery"
            date_upload = 0L
        }
        return listOf(chapter)
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(let template)")?.data() ?: return emptyList()

        val regex = Regex("""src=["'](books/[^"']+)["']""")
        return regex.findAll(script).mapIndexed { index, matchResult ->
            Page(index, imageUrl = "$baseUrl/${matchResult.groupValues[1]}")
        }.toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
