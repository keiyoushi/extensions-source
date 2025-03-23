package eu.kanade.tachiyomi.extension.ja.comicmeteor

import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ComicMeteor : ParsedHttpSource() {

    override val name = "COMICメテオ"

    override val baseUrl = "https://comic-meteor.jp"

    override val lang = "ja"

    override val supportsLatest = false

    private val json = Injekt.get<Json>()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .apply {
            val interceptors = interceptors()
            val index = interceptors.indexOfFirst { "Brotli" in it.javaClass.simpleName }
            if (index >= 0) {
                interceptors.add(interceptors.removeAt(index))
            }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(
        "$baseUrl/wp-admin/admin-ajax.php?action=get_flex_titles_for_toppage&page=$page&get_num=16",
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)
        val manga = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = manga.size == 16

        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaSelector() = ".update_work_size .update_work_info_img a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        element.selectFirst("img")!!.let {
            title = it.attr("alt")
            thumbnail_url = it.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    private lateinit var directory: List<Element>

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comicsearch")
            .addPathSegment("")
            .addQueryParameter("search", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        directory = document.select(searchMangaSelector())
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val endRange = minOf(page * 24, directory.size)
        val manga = directory.subList((page - 1) * 24, endRange).map { searchMangaFromElement(it) }
        val hasNextPage = endRange < directory.lastIndex

        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaSelector() = ".read_comic_size .read_comic_info_img a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h2.h2ttl")!!.text()
        author = document.selectFirst(".work_author_intro_name")
            ?.text()
            ?.substringAfter("著者 ：")
        description = document.selectFirst(".work_story_txt")?.text()
        genre = document.select(".category_link_box a").joinToString { it.text() }
        thumbnail_url = document.selectFirst(".latest_info_img img")?.absUrl("src")
    }

    override fun chapterListSelector() = ".work_episode_box .work_episode_table:has(.work_episode_link_orange)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.selectFirst(".work_episode_txt")!!.ownText()
    }

    private val reader by lazy { SpeedBinbReader(client, headers, json) }

    override fun pageListParse(document: Document) =
        reader.pageListParse(document)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
