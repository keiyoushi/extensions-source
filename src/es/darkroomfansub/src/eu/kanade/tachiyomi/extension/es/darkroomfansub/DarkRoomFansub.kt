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
        return GET("$url#override-search", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url
        if (url.toString().contains("override-search").not()) {
            return super.searchMangaParse(response)
        }

        val mangas = response.asJsoup()
            .select(".grid.gtc-f141a > div")
            .map(::searchMangaFromElement)

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a:not(:has(img))")
        title = anchor!!.ownText()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(anchor!!.absUrl("href"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = document
            .select(".series-chapterlist .flexch-infoz a")
            .map(::toSChapter)
            .reversed()
            .toMutableList()

        return chapters.takeIf { it.isNotEmpty() }
            ?: loadUngroupedChapters(document)
    }

    private fun loadUngroupedChapters(document: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val firstChapterLink = document.selectFirst("h4 a")?.absUrl("href")
            ?: return emptyList()

        return try {
            var chapterListUrl = client.newCall(GET(firstChapterLink, headers))
                .execute()
                .asJsoup()
                .selectFirst("h1 + .tac a")!!
                .absUrl("href")

            do {
                val chapterUngroup = client.newCall(GET(chapterListUrl, headers))
                    .execute()
                    .asJsoup()

                chapterUngroup.select(".grid.gtc-f141a > div > a")
                    .map(::toSChapter)
                    .also {
                        chapters += it
                    }
                val nextChapterPage = chapterUngroup.selectFirst("#Blog1_blog-pager-older-link")
                    ?.let {
                        chapterListUrl = it.absUrl("href")
                    }
            } while (nextChapterPage != null)

            chapters
                .sortedBy { it.name }
                .reversed()
        } catch (_: Exception) {
            chapters
        }
    }

    private fun toSChapter(element: Element) = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }
}
