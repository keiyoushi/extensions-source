package eu.kanade.tachiyomi.extension.en.clonemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class CloneManga : HttpSource() {

    override val name = "Clone Manga"
    override val baseUrl = "https://manga.clone-army.org"
    override val lang = "en"
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/viewer_landing.php", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.getElementsByClass("comicPreviewContainer").map { element ->
            val attr = element.getElementsByClass("comicPreview").attr("style")
            SManga.create().apply {
                title = element.select("h3").first()!!.text()
                artist = "Dan Kim"
                author = "Dan Kim"
                status = SManga.UNKNOWN
                url = "/" + element.select("a").first()!!.attr("href").removePrefix("/")
                description = element.select("h4").first()?.text() ?: ""
                thumbnail_url = "$baseUrl/" + attr.substring(
                    attr.indexOf("site/themes"),
                    attr.indexOf(")"),
                )
            }
        }
        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1).map { mp ->
        MangasPage(mp.mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = SManga.create()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val seriesPath = document.location().removePrefix(baseUrl)
        val scriptContent = document.getElementsByTag("script")[3].toString()
        val numChapters = PAGE_REGEX.findAll(scriptContent)
            .elementAt(3).destructured.component1()
            .toInt()

        val chapters = ArrayList<SChapter>(numChapters)
        for (i in 1..numChapters) {
            chapters.add(
                SChapter.create().apply {
                    url = "$seriesPath&page=$i"
                    name = "Chapter $i"
                    chapter_number = i.toFloat()
                },
            )
        }
        return chapters.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imgAbsoluteUrl = document.getElementsByClass("subsectionContainer").first()!!
            .select("img").first()!!.absUrl("src")
        return listOf(Page(1, imageUrl = imgAbsoluteUrl))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val PAGE_REGEX = Regex("""&page=(.*)&lang=""")
    }
}
