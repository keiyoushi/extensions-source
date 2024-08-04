package eu.kanade.tachiyomi.extension.es.darkroomfansub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DarkRoomFansub : ZeistManga(
    "Dark Room Fansub",
    "https://lector-darkroomfansub.blogspot.com",
    "es",
) {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val mangaDetailsSelectorDescription = ".ch-title + p"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$query")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val pathSegments = response.request.url.pathSegments
        if (pathSegments.contains("search").not()) {
            return super.searchMangaParse(response)
        }

        val mangas = response.asJsoup()
            .select(".grid.gtc-f141a > div")
            .map(::searchMangaFromElement)

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a:not(:has(img))")!!
        title = anchor.ownText()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(anchor.absUrl("href"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = chapterListFromDocument(document).reversed()
        return chapters.takeIf { it.isNotEmpty() }
            ?: loadUngroupedChapters(document)
    }

    private fun loadUngroupedChapters(document: Document): List<SChapter> {
        val firstChapterURL = document.selectFirst("h4 a")?.absUrl("href")
            ?: return emptyList()
        return try {
            val chapters = mutableListOf<SChapter>()
            var url = getChapterListURL(firstChapterURL)

            do {
                val chapterUngroup = fetchChapterList(url)

                chapters += chapterListFromDocument(chapterUngroup)

                val nextChapterPage = chapterUngroup
                    .selectFirst("#Blog1_blog-pager-older-link")?.also {
                        url = it.absUrl("href")
                    }
            } while (nextChapterPage != null)

            chapters
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun chapterListFromDocument(document: Document) =
        document.select(".grid.gtc-f141a > div > a, .series-chapterlist .flexch-infoz a")
            .map(::toSChapter)

    private fun getChapterListURL(url: String): String =
        fetchChapterList(url)
            .selectFirst("h1 + .tac a")!!
            .absUrl("href")

    private fun fetchChapterList(url: String) =
        client.newCall(GET(url, headers)).execute()
            .asJsoup()

    private fun toSChapter(element: Element) = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }
}
