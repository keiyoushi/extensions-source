package eu.kanade.tachiyomi.extension.en.purplecress

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class PurpleCress : HttpSource() {
    override val name = "Purple Cress"

    override val baseUrl = "https://purplecress.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET(baseUrl)

    override fun popularMangaParse(response: Response): MangasPage {
        val seriesContainer = response.asJsoup().selectFirst("div.container-grid--small")!!
        val mangaList: List<SManga> = seriesContainer.select("a").map {
            SManga.create().apply {
                title = it.selectFirst("div.card__info")!!.selectFirst("h3")!!.html()
                url = it.attr("href")
                author = it.selectFirst("p.card__author")!!.html().substringAfter("by ")
                artist = author
                description = it.attr("description")
                thumbnail_url = it.selectFirst("img.image")!!.attr("src")
                status = when (it.selectFirst("h3.card__status")!!.html()) {
                    "Ongoing" -> SManga.ONGOING
                    "Dropped" -> SManga.COMPLETED // Not sure what the best status is for "Dropped"
                    "Completed" -> SManga.COMPLETED // There aren't any completed series on the site, so I'm just guessing as to the string
                    else -> SManga.UNKNOWN
                }
                initialized = true // We have all the fields
            }
        }
        return MangasPage(mangaList, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val seriesContainer = response.asJsoup().selectFirst("div.container-grid--large")!!
        val mangaList: List<SManga> = seriesContainer.select("a").map {
            SManga.create().apply {
                title = it.selectFirst("h3.chapter__series-name")!!.html()
                url = it.attr("href").replaceFirst("chapter", "series").substringBeforeLast("/")
                thumbnail_url = it.selectFirst("img.image")!!.attr("src")
                initialized = false
            }
        }
        return MangasPage(mangaList, false)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val oldUrl = manga.url
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply {
                    initialized = true
                    url = oldUrl // Sets URL in result to original URL
                }
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request = chapterListRequest(manga)

    override fun mangaDetailsParse(response: Response): SManga {
        val responseJ = response.asJsoup()
        val infoBox = responseJ.selectFirst("div.series__info")!!
        return SManga.create().apply {
            title = infoBox.selectFirst("h1.series__name")!!.html()
            // url is set by overridden fetchMangaDetails
            author = infoBox.selectFirst("p.series__author")!!.html().substringAfter("by ")
            artist = author
            description = infoBox.selectFirst("p.description-pagagraph")!!.html()
            thumbnail_url = responseJ.selectFirst("img.thumbnail")!!.attr("src")
            status = when (infoBox.selectFirst("span.series__status")!!.html()) {
                "Ongoing" -> SManga.ONGOING
                "Dropped" -> SManga.COMPLETED // See comments in popularMangaParse
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("a.chapter__card")
            .map {
                SChapter.create().apply {
                    url = it.attr("href")
                    name = it.selectFirst("span.chapter__name")!!.html()
                    date_upload = it.selectFirst("h5.chapter__date")!!.html()
                        .let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time ?: 0L }
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select("img.page__img").mapIndexed { index, element ->
            Page(index, "", element.attr("src"))
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl)
    }

    companion object {
        const val URL_SEARCH_PREFIX = "purplecress_url:"
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val manga = SManga.create().apply {
                url = query.removePrefix(URL_SEARCH_PREFIX)
            }
            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        }
        return fetchPopularManga(page).map {
                mangasPage ->
            MangasPage(
                mangasPage.mangas.filter {
                    it.title.contains(query, true)
                },
                mangasPage.hasNextPage,
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
