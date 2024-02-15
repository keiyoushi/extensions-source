package eu.kanade.tachiyomi.extension.ja.ohtawebcomic

import eu.kanade.tachiyomi.multisrc.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class OhtaWebComic : SpeedBinbReader(true) {

    override val name = "Ohta Web Comic"

    override val baseUrl = "https://webcomic.ohtabooks.com"

    override val lang = "ja"

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private lateinit var directory: List<Element>

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        directory = document.select(".bnrList ul li a")
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val endRange = minOf(page * 24, directory.size)
        val manga = directory.subList((page - 1) * 24, endRange).map {
            SManga.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst(".pic img")?.absUrl("src")
            }
        }
        val hasNextPage = endRange < directory.lastIndex

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, query, filters) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, query: String, filters: FilterList): MangasPage {
        val document = response.asJsoup()

        directory = document.select(".bnrList ul li a")
            .filter { it ->
                it.selectFirst(".title")?.text()?.contains(query, true) == true
            }
        return parseDirectory(1)
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
            .map {
                SChapter.create().apply {
                    url = "/contents/${it.getChapterId()}"
                    name = it.selectFirst("div.title")!!.text()
                }
            }

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

    override fun pageListRequest(chapter: SChapter) =
        GET("https://www.yondemill.jp${chapter.url}?view=1&u0=1", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val readerUrl = document.selectFirst("script:containsData(location.href)")!!
            .data()
            .substringAfter("location.href='")
            .substringBefore("';")
        val headers = headers.newBuilder()
            .set("Referer", response.request.url.toString())
            .build()
        val readerResponse = client.newCall(GET(readerUrl, headers)).execute()

        return super.pageListParse(readerResponse)
    }
}

private fun Element.getChapterId(): String =
    attr("onclick")
        .substringAfter("openBook('")
        .substringBefore("')")
