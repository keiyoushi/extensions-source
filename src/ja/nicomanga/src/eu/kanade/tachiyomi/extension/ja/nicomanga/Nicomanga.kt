package eu.kanade.tachiyomi.extension.ja.nicomanga

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URL

class Nicomanga : HttpSource() {
    override val baseUrl: String = "https://nicomanga.com"
    override val lang: String = "ja"
    override val name: String = "Nicomanga"
    override val supportsLatest: Boolean = true
    private val application: Application by injectLazy()
    private val thumbnailURLRegex: Regex = "background-image:[^;]url\\s*\\(\\s*'([^?']+)".toRegex()
    private val statusRegex: Regex = "(?<=-)[^.]+".toRegex()
    private val urlRegex: Regex = "(?<=manga-)[^/]+(?=\\.html\$)".toRegex()
    private val floatRegex: Regex = "\\d+(?:\\.\\d+)?".toRegex()
    private val chapterIdRegex: Regex = "(?<=imgsListchap\\()\\d+".toRegex()
    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        val headers = request.headers.newBuilder()
        if ((request.headers["Referer"] ?: "") != "") {
            headers.add("Referer", baseUrl)
        }
        chain.proceed(request.newBuilder().headers(headers.build()).build())
    }.build()

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
        val mangas = ArrayList<SManga>()
        for (manga in mangaList) {
            val url = manga.selectFirst(".series-title a")?.attr("href") ?: ""
            mangas.add(
                SManga.create().apply {
                    setUrlWithoutDomain(URL(URL(baseUrl), url).toString())
                    title = manga.selectFirst(".series-title")?.text() ?: ""
                    thumbnail_url = thumbnailURLRegex.find(manga.selectFirst(".img-in-ratio.lazyloaded")?.attr("style") ?: "")?.groupValues?.get(1)
                },
            )
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-list.html?page=$page&sort=last_update&sort_type=DESC")

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list.html?page=$page&sort=views&sort_type=DESC")

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/manga-list.html?page=$page&artist=&author=&group=&m_status=&name=$query&genre=&ungenre=&magazine=&sort=last_update&sort_type=DESC")

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val doc = Jsoup.parse(response.body.string())
        author = doc.select("ul.manga-info a[href^=\"manga-author\"]").joinToString { it.text() }
        genre = doc.select("ul.manga-info a[href^=\"manga-list-genre\"]").joinToString { it.text() }
        val statusText = statusRegex.find(doc.select(".manga-info li:has(i.fa-spinner) a").attr("href"))?.groupValues?.get(0) ?: ""
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapters = ArrayList<SChapter>()
        val chapterList = doc.select("ul > a")
        val mangaId = response.request.url.toUrl().toString().substringAfterLast('/').substringBeforeLast('.')
        val sharedPref = application.getSharedPreferences("source_${id}_time_found:$mangaId", 0)
        val editor = sharedPref.edit()
        var lastNum = 0f
        for (chapter in chapterList) {
            chapters.add(
                SChapter.create().apply {
                    name = chapter.attr("title").trim()
                    setUrlWithoutDomain(URL(URL(baseUrl), chapter.attr("href")).toString())
                    chapter_number = floatRegex.find(chapter.attr("title").trim())?.groupValues?.get(0)?.toFloat() ?: (lastNum + 0.01f)
                    lastNum = chapter_number
                    val dateFound = System.currentTimeMillis()
                    if (!sharedPref.contains(chapter_number.toString())) {
                        editor.putLong(chapter_number.toString(), dateFound)
                    }
                    date_upload = sharedPref.getLong(chapter_number.toString(), dateFound)
                },
            )
        }
        editor.apply()
        return chapters
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = urlRegex.find(manga.url)?.groupValues?.get(0) ?: ""
        return GET("$baseUrl/app/manga/controllers/cont.Listchapterapi.php?slug=$slug")
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val pages = ArrayList<Page>()
        // Nicovideo will refuse to serve any pages if the user has not logged in
        if (!doc.select("#login_manga").isEmpty()) {
            throw SecurityException("Not logged in. Please login via WebView first")
        }
        val pageList = doc.select("#page_contents > li")
        for (page in pageList) {
            val pageNumber = page.attr("data-page-index").toInt()
            val url = page.select("div > img").attr("data-original")
            pages.add(Page(pageNumber, url, url))
        }
        return pages
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val r = client.newCall(GET(getChapterUrl(chapter))).execute()
        val id = chapterIdRegex.find(r.body.string())?.groupValues?.get(0) ?: throw Exception("chapter-id not found")
        val headers = headersBuilder().set("referer", getChapterUrl(chapter)).build()
        return GET("$baseUrl/app/manga/controllers/cont.imgsList.php?cid=$id", headers)
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder().set("referer", baseUrl).build()
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")
}
