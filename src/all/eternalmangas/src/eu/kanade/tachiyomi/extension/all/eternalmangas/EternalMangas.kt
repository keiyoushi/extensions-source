package eu.kanade.tachiyomi.extension.all.eternalmangas

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.multisrc.mangaesp.SeriesDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

open class EternalMangas(
    lang: String,
    private val internalLang: String,
) : MangaEsp(
    "EternalMangas",
    "https://eternalmangas.com",
    lang,
) {
    override val useApiSearch = true

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return super.fetchSearchManga(page, "", createSortFilter("views", false))
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchSearchManga(page, "", createSortFilter("updated_at", false))
    }

    override fun List<SeriesDto>.additionalParse(): List<SeriesDto> {
        return this.filter { it.language == internalLang }.toMutableList()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/comics", headers)
    }

    private val tokenRegex = """self\.__next_f\.push\(.*token\\":\\"(.*?)\\"\}""".toRegex()

    override fun searchMangaParse(
        response: Response,
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val token = tokenRegex.find(response.body.string())?.groupValues?.get(1)
            ?: throw Exception("Token not found")

        val apiHeaders = headersBuilder()
            .add("X-Eternal-Key", token)
            .build()

        val apiResponse = client.newCall(GET("$apiBaseUrl$apiPath/comics-actu", apiHeaders)).execute()
        return super.searchMangaParse(apiResponse, page, query, filters)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val body = jsRedirect(response)

        MANGA_DETAILS_REGEX.find(body)?.groupValues?.get(1)?.let {
            val unescapedJson = it.unescape()
            return json.decodeFromString<SeriesDto>(unescapedJson).toSMangaDetails()
        }

        val document = Jsoup.parse(body)
        with(document.selectFirst("div#info")!!) {
            title = select("div:has(p.font-bold:contains(Títuto)) > p.text-sm").text()
            author = select("div:has(p.font-bold:contains(Autor)) > p.text-sm").text()
            artist = select("div:has(p.font-bold:contains(Artista)) > p.text-sm").text()
            genre = select("div:has(p.font-bold:contains(Género)) > p.text-sm > span").joinToString { it.ownText() }
        }
        description = document.select("div#sinopsis p").text()
        thumbnail_url = document.selectFirst("div.contenedor img.object-cover")?.imgAttr()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = jsRedirect(response)

        MANGA_DETAILS_REGEX.find(body)?.groupValues?.get(1)?.let {
            val unescapedJson = it.unescape()
            val series = json.decodeFromString<SeriesDto>(unescapedJson)
            return series.chapters.map { chapter -> chapter.toSChapter(seriesPath, series.slug) }
        }

        val document = Jsoup.parse(body)
        return document.select("div.contenedor > div.grid > div > a").map {
            SChapter.create().apply {
                name = it.selectFirst("span.text-sm")!!.text()
                date_upload = try {
                    it.selectFirst("span.chapter-date")?.attr("data-date")?.let { date ->
                        dateFormat.parse(date)?.time
                    } ?: 0
                } catch (e: ParseException) {
                    0
                }
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(jsRedirect(response))
        return doc.select("main > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    private fun jsRedirect(response: Response): String {
        var body = response.body.string()
        val document = Jsoup.parse(body)
        document.selectFirst("body > form[method=post], body > div[hidden] > form[method=post]")?.let {
            val action = it.attr("action")
            val inputs = it.select("input")

            val form = FormBody.Builder()
            inputs.forEach { input ->
                form.add(input.attr("name"), input.attr("value"))
            }

            body = client.newCall(POST(action, headers, form.build())).execute().body.string()
        }
        return body
    }

    private fun createSortFilter(value: String, ascending: Boolean = false): FilterList {
        val sortProperties = getSortProperties()
        val index = sortProperties.indexOfFirst { it.value == value }.takeIf { it >= 0 } ?: 0
        return FilterList(
            SortByFilter("", sortProperties).apply {
                state = Filter.Sort.Selection(index, ascending)
            },
        )
    }
}
