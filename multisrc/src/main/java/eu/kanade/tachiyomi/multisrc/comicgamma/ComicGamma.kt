package eu.kanade.tachiyomi.multisrc.comicgamma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

open class ComicGamma(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "ja",
) : ParsedHttpSource() {
    override val supportsLatest = false

    override val client = network.client.newBuilder().addInterceptor(PtImgInterceptor).build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector() = ".tab_panel.active .manga_item"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        url = element.selectFirst(Evaluator.Tag("a"))!!.attr("href")
        title = element.selectFirst(Evaluator.Class("manga_title"))!!.text()
        author = element.selectFirst(Evaluator.Class("manga_author"))!!.text()
        val genreList = element.select(Evaluator.Tag("li")).map { it.text() }
        genre = genreList.joinToString()
        status = when {
            genreList.contains("完結") && !genreList.contains("リピート配信") -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.absUrl("src")
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used.")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        fetchPopularManga(page).map { p -> MangasPage(p.mangas.filter { it.title.contains(query) }, false) }

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used.")
    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used.")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used.")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used.")

    override fun pageListParse(document: Document) =
        document.select("#content > div[data-ptimg]").mapIndexed { i, e ->
            Page(i, imageUrl = e.attr("abs:data-ptimg"))
        }

    override fun mangaDetailsParse(document: Document): SManga {
        val titleElement = document.selectFirst(Evaluator.Class("manga__title"))!!
        val titleName = titleElement.child(0).text()
        val desc = document.selectFirst(".detail__item > p:not(:empty)")?.run {
            select(Evaluator.Tag("br")).prepend("\\n")
            this.text().replace("\\n", "\n").replace("\n ", "\n")
        }
        val listResponse = client.newCall(popularMangaRequest(0)).execute()
        val manga = popularMangaParse(listResponse).mangas.find { it.title == titleName }
        return manga?.apply { description = desc } ?: SManga.create().apply {
            author = titleElement.child(1).text()
            description = desc
            status = SManga.UNKNOWN
            val slug = document.location().removeSuffix("/").substringAfterLast("/")
            thumbnail_url = "$baseUrl/img/manga_thumb/${slug}_list.jpg"
        }
    }

    override fun chapterListSelector() = ".read__area > .read__outer > a:not([href=#comics])"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = element.attr("href").toOldChapterUrl()
        val number = url.removeSuffix("/").substringAfterLast('/').replace('_', '.')
        val list = element.selectFirst(Evaluator.Class("read__contents"))!!.children()
        name = "[$number] ${list[0].text()}"
        if (list.size >= 3) {
            date_upload = dateFormat.parseJST(list[2].text())?.time ?: 0L
        }
    }

    override fun pageListRequest(chapter: SChapter) =
        GET(baseUrl + chapter.url.toNewChapterUrl(), headers)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    companion object {
        internal fun SimpleDateFormat.parseJST(date: String) = parse(date)?.apply {
            time += 12 * 3600 * 1000 // updates at 12 noon
        }

        private fun getJSTFormat(datePattern: String) =
            SimpleDateFormat(datePattern, Locale.JAPANESE).apply {
                timeZone = TimeZone.getTimeZone("GMT+09:00")
            }

        private val dateFormat by lazy { getJSTFormat("yyyy年M月dd日") }

        private fun String.toOldChapterUrl(): String {
            // ../../../_files/madeinabyss/063_2/
            val segments = split('/')
            val size = segments.size
            val slug = segments[size - 3]
            val number = segments[size - 2]
            return "/manga/$slug/_files/$number/"
        }

        private fun String.toNewChapterUrl(): String {
            val segments = split('/')
            return "/_files/${segments[2]}/${segments[4]}/"
        }
    }
}
