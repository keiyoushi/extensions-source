package eu.kanade.tachiyomi.extension.pt.starlightscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class StarlightScan : MangaThemesia(
    "Starlight Scan",
    "https://starligthscan.com",
    "pt-BR",
    mangaUrlDirectory = "/mangas",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val sendViewCount = false

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangaList = response.asJsoup()
            .select(latestUpdatesSelector())
            .map(::latestUpdatesFromElement)

        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun latestUpdatesSelector() = "div.mostRecentMangaCard__listContainer article.mostRecentMangaCard"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a.mostRecentMangaCard__title")!!.text()
        thumbnail_url = element.selectFirst("img.mostRecentMangaCard__cover")!!.imgAttr()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(if (query.isEmpty()) mangaUrlDirectory.substring(1) else "buscar")
            .addQueryParameter("search", query)
            .addQueryParameter("page-current", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.bulkMangaList article.bulkMangaCard"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a.bulkMangaCard__title")!!.text()
        thumbnail_url = element.selectFirst("img.bulkMangaCard__cover")!!.imgAttr()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaNextPageSelector() = "footer.base__horizontalList a:contains(Próxima):not([disabled])"

    override val seriesDetailsSelector = "section.mangaDetails"
    override val seriesTitleSelector = "h1.mangaDetails__title"
    override val seriesAuthorSelector = "span.mangaDetails__author"
    override val seriesDescriptionSelector = "span.mangaDetails__description"
    override val seriesGenreSelector = "li.mangaTags__item"
    override val seriesStatusSelector = "span.base__horizontalList[title^=Status]"
    override val seriesThumbnailSelector = "img.mangaDetails__cover"

    override fun String?.parseStatus(): Int = when (this) {
        "Publicação Finalizada" -> SManga.COMPLETED
        else -> SManga.ONGOING
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup()
            .select(chapterListSelector())
            .map(::chapterFromElement)
            .filter { it.name.isNotEmpty() }
    }

    override fun chapterListSelector() = "div.mangaDetails__episodesContainer div.mangaDetails__episode"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("a.mangaDetails__episodeTitle")!!.text()
        date_upload = element.selectFirst("span.mangaDetails__episodeReleaseDate")?.text().parseChapterDate()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override val pageSelector = "div.scanImagesContainer img.scanImage"

    override fun getFilterList(): FilterList = FilterList()
}
