package eu.kanade.tachiyomi.extension.tr.epikman

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class Epikman : ZeistManga(
    "Epikman",
    "https://www.epikman.ga",
    "tr",
) {
    override val useOldChapterFeed = true
    override val chapterCategory = "Bölüm"
    override val pageListSelector = ".chapter-view"

    private var nextLatestPageUrl: String = ""

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            nextLatestPageUrl = ""
        }

        val url = nextLatestPageUrl.ifBlank {
            "$baseUrl/search/label/Seri?max-results=20"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mangaListParse(document)
        val nextPage = document.selectFirst("a[title='Önceki Kayıtlar']")

        nextPage?.let {
            nextLatestPageUrl = it.absUrl("href")
        }

        return MangasPage(mangas, nextPage != null)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("max-results", "999")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) =
        MangasPage(mangaListParse(response.asJsoup()), false)

    private fun mangaListParse(document: Document) =
        document.select("#Blog1 .grid > div").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".clamp")!!) {
                    title = text()
                    setUrlWithoutDomain(absUrl("href"))
                }
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
}
