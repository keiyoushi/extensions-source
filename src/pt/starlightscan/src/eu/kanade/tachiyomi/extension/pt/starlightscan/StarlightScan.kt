package eu.kanade.tachiyomi.extension.pt.starlightscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class StarlightScan : MangaThemesia() {
    override val mangaUrlDirectory = "/mangas"
    override val datePattern = "dd/MM/yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val sendViewCount = false

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangaList = client.get(baseUrl).asJsoup()
            .select("div.mostRecentMangaCard__listContainer article.mostRecentMangaCard")
            .map { element ->
                SManga.create().apply {
                    title = element.selectFirst("a.mostRecentMangaCard__title")!!.text()
                    thumbnail_url = element.selectFirst("img.mostRecentMangaCard__cover")!!.imgAttr()
                    setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                }
            }

        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(if (query.isEmpty()) mangaUrlDirectory.substring(1) else "buscar")
        .addQueryParameter("search", query)
        .addQueryParameter("page-current", page.toString())

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

    override fun chapterListSelector() = "div.mangaDetails__episodesContainer div.mangaDetails__episode"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("a.mangaDetails__episodeTitle")!!.text()
        date_upload = element.selectFirst("span.mangaDetails__episodeReleaseDate")?.text().parseChapterDate()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override val pageSelector = "div.scanImagesContainer img.scanImage"

    override fun getFilterList(data: JsonElement?) = FilterList()
}
