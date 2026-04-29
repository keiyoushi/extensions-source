package eu.kanade.tachiyomi.extension.ja.ohtawebcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import keiyoushi.utils.jsonInstance
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class OhtaWebComic : HttpSource() {

    override val name = "Ohta Web Comic"

    override val baseUrl = "https://webcomic.ohtabooks.com"

    override val lang = "ja"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(jsonInstance))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val directory = document.select(".bnrList ul li a")

                val startIndex = (page - 1) * 24
                if (startIndex >= directory.size) {
                    return@map MangasPage(emptyList(), false)
                }

                val endRange = minOf(page * 24, directory.size)
                val manga = directory.subList(startIndex, endRange).map(::parseMangaFromElement)
                val hasNextPage = endRange < directory.size

                MangasPage(manga, hasNextPage)
            }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/", headers)

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val directory = document.select(".bnrList ul li a")
                    .filter { it.selectFirst(".title")?.text()?.contains(query, true) == true }

                val startIndex = (page - 1) * 24
                if (startIndex >= directory.size) {
                    return@map MangasPage(emptyList(), false)
                }

                val endRange = minOf(page * 24, directory.size)
                val manga = directory.subList(startIndex, endRange).map(::parseMangaFromElement)
                val hasNextPage = endRange < directory.size

                MangasPage(manga, hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/list/", headers)

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    private fun parseMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst(".title")!!.text()
        thumbnail_url = element.selectFirst(".pic img")?.absUrl("src")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("[itemprop=name]")!!.text()
            author = document.selectFirst("[itemprop=author]")?.text()
            thumbnail_url = document.selectFirst(".contentHeader")
                ?.attr("style")
                ?.substringAfter("background-image:url(")
                ?.substringBefore(");")
            description = buildString {
                var currentNode = document.selectFirst("h3.titleBoader:contains(作品について) + dl")
                    ?: return@buildString

                while (true) {
                    val nextSibling = currentNode.nextElementSibling()
                        ?: break

                    if (nextSibling.nodeName() != "p") {
                        break
                    }

                    appendLine(nextSibling.text())
                    currentNode = nextSibling
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(".backnumberList a[onclick*=openBook]")
            .sortedByDescending {
                it.selectFirst("dt.number")!!.ownText().toInt()
            }
            .map { chapterFromElement(it) }

        if (chapters.isNotEmpty()) {
            return chapters
        }

        return document.select(".headBtnList a[onclick*=openBook]")
            .map {
                SChapter.create().apply {
                    url = "/contents/${it.getChapterId()}"
                    name = it.ownText()
                }
            }
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = "/contents/${element.getChapterId()}"
        name = element.selectFirst("div.title")!!.text()
    }

    private val reader by lazy { SpeedBinbReader(client, headers, jsonInstance, true) }

    override fun pageListRequest(chapter: SChapter) = GET("https://www.yondemill.jp${chapter.url}?view=1&u0=1", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val readerUrl = document.selectFirst("script:containsData(location.href)")!!
            .data()
            .substringAfter("location.href='")
            .substringBefore("';")

        val requestHeaders = headers.newBuilder()
            .set("Referer", response.request.url.toString())
            .build()

        val readerResponse = client.newCall(GET(readerUrl, requestHeaders)).execute()

        return reader.pageListParse(readerResponse)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

private fun Element.getChapterId(): String = attr("onclick")
    .substringAfter("openBook('")
    .substringBefore("')")
