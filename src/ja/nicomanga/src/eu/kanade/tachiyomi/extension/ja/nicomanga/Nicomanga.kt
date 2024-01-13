package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Nicomanga : HttpSource() {
    companion object {
        private val thumbnailURLRegex: Regex = "background-image:[^;]url\\s*\\(\\s*'([^?']+)".toRegex()

        private val statusRegex: Regex = "-([^.]+)".toRegex()

        private val urlRegex: Regex = "manga-([^/]+)\\.html\$".toRegex()

        private val chapterIdRegex: Regex = "imgsListchap\\((\\d+)".toRegex()
    }

    override val baseUrl: String = "https://nicomanga.com"

    override val lang: String = "ja"

    override val name: String = "Nicomanga"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun mangaListParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val hasNextPage = (
            doc.select(".pagination li:last-of-type").size > 0 &&
                doc.select(".pagination li:last-of-type")[0].text() == "»" &&
                doc.select(".pagination li:last-of-type a.disabled").size == 0
            ) || doc.select(".pagination li:last-of-type a.active").size == 0

        val mangas = doc.select(".row > .thumb-item-flow").map { manga ->
            SManga.create().apply {
                setUrlWithoutDomain(manga.selectFirst(".series-title a")!!.absUrl("href"))
                title = manga.selectFirst(".series-title")?.text()!!
                thumbnail_url = thumbnailURLRegex.find(manga.selectFirst(".img-in-ratio.lazyloaded")!!.attr("style"))!!.groupValues[1]
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
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url, headers)
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
        val doc = response.asJsoup()
        author = doc.select("ul.manga-info a[href^=\"manga-author\"]").joinToString { it.text() }
        genre = doc.select("ul.manga-info a[href^=\"manga-list-genre\"]").joinToString { it.text() }
        val statusText = statusRegex.find(doc.select(".manga-info li:has(i.fa-spinner) a").attr("href"))?.run { groupValues[1] }
        status = when (statusText) {
            "on-going" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = urlRegex.find(manga.url)!!.groupValues[1]
        return GET("$baseUrl/app/manga/controllers/cont.Listchapterapi.php?slug=$slug")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val chapterList = doc.select("ul > a")
        val chapterPrefix = "$baseUrl/app/manga/controllers"

        val chapters = chapterList.map { chapter ->
            SChapter.create().apply {
                name = chapter.attr("title").trim()
                val url = chapter.absUrl("href").run {
                    takeIf { startsWith(chapterPrefix) }
                        ?.replaceFirst(chapterPrefix, baseUrl)
                        ?: this
                }
                setUrlWithoutDomain(url)
            }
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val id = chapterIdRegex.find(response.body.string())?.groupValues?.get(1) ?: throw Exception("chapter-id not found")
        val r = client.newCall(GET("$baseUrl/app/manga/controllers/cont.imgsList.php?cid=$id", headers)).execute()
        val doc = r.asJsoup()
        return doc.select("img.chapter-img").mapIndexed { i, page ->
            Page(i + 1, imageUrl = page.attr("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")
}
