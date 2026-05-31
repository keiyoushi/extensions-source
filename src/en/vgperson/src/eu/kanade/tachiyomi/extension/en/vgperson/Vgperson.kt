package eu.kanade.tachiyomi.extension.en.vgperson

import android.os.Build.VERSION
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import rx.Observable

class Vgperson : HttpSource() {

    override val name = "vgperson"

    override val lang = "en"

    override val supportsLatest = false

    override val baseUrl = "https://vgperson.com/other/mangaviewer.php"

    private val userAgent =
        "Mozilla/5.0 (Android ${VERSION.RELEASE}; Mobile) Tachiyomi/${AppInfo.getVersionName()}"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)
        .add("User-Agent", userAgent)

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".content a[href^=?m]").map { element ->
            SManga.create().apply {
                title = element.text()
                url = element.attr("href")
                thumbnail_url = getCover(title)
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".title")!!.text()
            thumbnail_url = getCover(title)
            status = when (document.select("div.content .complete").text()) {
                "(Complete)" -> SManga.COMPLETED
                "(Series in Progress)" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            description = buildString {
                document.selectFirst(".content")!!.childNodes().drop(5).takeWhile {
                    it.nodeName() != "table"
                }.forEach {
                    if (it is TextNode) {
                        append(it.text())
                    } else if (it is Element) {
                        if (it.tagName() == "br") {
                            append("\n")
                        } else {
                            append(it.text())
                        }
                    }
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chaptertable tbody tr").map { element ->
            SChapter.create().apply {
                element.selectFirst("td > a")!!.let {
                    name = it.text()
                    url = it.attr("href")
                }

                // append the name if it exists & remove the occasional hyphen
                element.selectFirst("td:last-child:not(:first-child)")?.let {
                    name += " - ${it.text().substringAfter("- ")}"
                }

                val fullUrl = "$baseUrl$url".toHttpUrl()

                // hardcode special chapter numbers for Three Days of Happiness
                chapter_number = fullUrl.queryParameter("c")?.toFloat()
                    ?: (16.5f + fullUrl.queryParameter("b")!!.toFloat() / 10)
                scanlator = "vgperson"
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = fetchPopularManga(1).map { mp ->
        MangasPage(
            mp.mangas.filter { it.title.contains(query, ignoreCase = true) },
            false,
        )
    }

    // get known manga covers from imgur
    private fun getCover(title: String) = when (title) {
        "The Festive Monster's Cheerful Failure" -> "kEK10GL.png"
        "Azure and Claude" -> "buXnlmh.jpg"
        "Three Days of Happiness" -> "kL5dvnp.jpg"
        else -> null
    }?.let { "https://i.imgur.com/$it" }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
