package eu.kanade.tachiyomi.extension.all.meituatop

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
import org.jsoup.select.Evaluator
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

// Uses MACCMS http://www.maccms.la/
class MeituaTop : HttpSource() {
    override val name = "Meitua.top"
    override val lang = "all"
    override val supportsLatest = false

    override val baseUrl = "https://7a.meitu1.mom"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/arttype/0b-$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst(Evaluator.Class("thumbnail-group"))!!.children().map {
            SManga.create().apply {
                url = it.selectFirst(Evaluator.Tag("a"))!!.attr("href")
                val image = it.selectFirst(Evaluator.Tag("img"))!!
                title = image.attr("alt")
                thumbnail_url = image.attr("src")
                val info = it.selectFirst(Evaluator.Tag("p"))!!.ownText().split(" - ")
                genre = info[0]
                description = info[1]
                status = SManga.COMPLETED
                initialized = true
            }
        }
        val pageLinks = document.select(Evaluator.Class("page_link"))
        if (pageLinks.isEmpty()) return MangasPage(mangas, false)
        val lastPage = pageLinks[3].attr("href")
        val hasNextPage = document.location().pageNumber() != lastPage.pageNumber()
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/artsearch/-------.html".toHttpUrl().newBuilder()
                .addQueryParameter("wd", query)
                .addQueryParameter("page", page.toString())
                .toString()
            return GET(url, headers)
        }

        val filter = filters.filterIsInstance<RegionFilter>().firstOrNull() ?: return popularMangaRequest(page)
        return GET("$baseUrl/arttype/${21 + filter.state}b-$page.html", headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = parseDate(manga.description!!)
            chapter_number = -2f
        }
        return Observable.just(listOf(chapter))
    }

    private fun parseDate(date: String): Long = runCatching {
        dateFormat.parse(date)?.time
    }.getOrNull() ?: 0L

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.selectFirst(Evaluator.Class("ttnr"))!!.select(Evaluator.Tag("img"))
            .map { it.attr("src") }.distinct()
        return images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Category (ignored for text search)"),
        RegionFilter(),
    )

    private class RegionFilter : Filter.Select<String>(
        "Region",
        arrayOf("All", "国产美女", "韩国美女", "台湾美女", "日本美女", "欧美美女", "泰国美女"),
    )

    private fun String.pageNumber() = numberRegex.findAll(this).last().value.toInt()

    private val numberRegex by lazy { Regex("""\d+""") }

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
}
