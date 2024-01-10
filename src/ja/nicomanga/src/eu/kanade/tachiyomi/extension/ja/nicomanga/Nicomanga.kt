package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL

class Nicomanga : HttpSource() {
    companion object {
        val thumbnailURLRegex: Regex = "background-image:[^;]url\\s*\\(\\s*'([^?']+)".toRegex()

        val statusRegex: Regex = "(?<=-)[^.]+".toRegex()

        val urlRegex: Regex = "(?<=manga-)[^/]+(?=\\.html\$)".toRegex()

        val floatRegex: Regex = "\\d+(?:\\.\\d+)?".toRegex()

        val chapterIdRegex: Regex = "(?<=imgsListchap\\()\\d+".toRegex()
    }

    override val baseUrl: String = "https://nicomanga.com"

    override val lang: String = "ja"

    override val name: String = "Nicomanga"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient

    private fun mangaListParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val mangaList: ArrayList<Element> = doc.select(".row > .thumb-item-flow")
        val hasNextPage =
            if (doc.select(".pagination li:last-of-type").size > 0 &&
                doc.select(".pagination li:last-of-type")[0].text() == "»"
            ) {
                doc.select(".pagination li:last-of-type a.disabled").size == 0
            } else {
                doc.select(".pagination li:last-of-type a.active").size == 0
            }
        val mangas = doc.select(".row > .thumb-item-flow").map { manga ->
            SManga.create().apply {
                val relURL = manga.selectFirst(".series-title a")?.attr("href") ?: ""
                setUrlWithoutDomain(URL(URL(baseUrl), relURL).toString())
                title = manga.selectFirst(".series-title")?.text() ?: ""
                thumbnail_url = Nicomanga.thumbnailURLRegex.find(manga.selectFirst(".img-in-ratio.lazyloaded")?.attr("style") ?: "")?.groupValues?.get(1)
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("artist", "")
            .addQueryParameter("author", "")
            .addQueryParameter("group", "")
            .addQueryParameter("m_status", "")
            .addQueryParameter("name", query)
            .addQueryParameter("genre", "")
            .addQueryParameter("ungenre", "")
            .addQueryParameter("magazine", "")
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val doc = Jsoup.parse(response.body.string())
        author = doc.select("ul.manga-info a[href^=\"manga-author\"]").joinToString { it.text() }
        genre = doc.select("ul.manga-info a[href^=\"manga-list-genre\"]").joinToString { it.text() }
        val statusText = Nicomanga.statusRegex.find(doc.select(".manga-info li:has(i.fa-spinner) a").attr("href"))?.groupValues?.get(0) ?: ""
        status = when (statusText) {
            "on-going" -> {
                SManga.ONGOING
            }
            "completed" -> {
                SManga.COMPLETED
            }
            else -> {
                SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = Nicomanga.urlRegex.find(manga.url)?.groupValues?.get(0) ?: ""
        return GET("$baseUrl/app/manga/controllers/cont.Listchapterapi.php?slug=$slug")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapterList = doc.select("ul > a")
        var lastNum = 0f
        val chapters = chapterList.map { chapter ->
            SChapter.create().apply {
                name = chapter.attr("title").trim()
                setUrlWithoutDomain(URL(URL(baseUrl), chapter.attr("href")).toString())
                chapter_number = Nicomanga.floatRegex.find(chapter.attr("title").trim())?.groupValues?.get(0)?.toFloat() ?: (lastNum + 0.01f)
                lastNum = chapter_number
            }
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val r = client.newCall(GET(getChapterUrl(chapter))).execute()
        val id = Nicomanga.chapterIdRegex.find(r.body.string())?.groupValues?.get(0) ?: throw Exception("chapter-id not found")
        val headers = headersBuilder().set("referer", getChapterUrl(chapter)).build()
        return GET("$baseUrl/app/manga/controllers/cont.imgsList.php?cid=$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val pages = ArrayList<Page>()
        // Nicovideo will refuse to serve any pages if the user has not logged in
        val pageList = doc.select("img.chapter-img")
        for ((i, page) in pageList.withIndex()) {
            val url = page.attr("data-src")
            pages.add(Page(i + 1, url, url))
        }
        return pages
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder().set("referer", baseUrl).build()
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")
}
