package eu.kanade.tachiyomi.extension.ja.nikkangecchan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Nikkangecchan : HttpSource() {

    override val name = "Nikkangecchan"

    override val baseUrl = "https://nikkangecchan.jp"

    override val lang = "ja"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".contentInner > figure").mapNotNull { element ->
            val imgBox = element.selectFirst(".imgBox")
            val detailBox = element.select(".detailBox").lastOrNull()

            val mangaTitle = detailBox?.selectFirst("h3")?.text() ?: return@mapNotNull null
            val mangaUrl = imgBox?.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                thumbnail_url = imgBox.selectFirst("a > img")?.attr("abs:src")
                setUrlWithoutDomain(mangaUrl)
            }
        }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = super.fetchSearchManga(page, query, filters).map { mangasPage ->
        val filtered = mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
        MangasPage(filtered, false)
    }

    // Does not have search, use complete list (in popular) instead.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val detailBox = document.selectFirst("#comicDetail .detailBox")
            ?: throw Exception("Detail box not found")

        return SManga.create().apply {
            title = detailBox.selectFirst("h3")?.text() ?: ""
            author = detailBox.selectFirst(".author")?.text()
            artist = author
            description = document.selectFirst(".description")?.text()
            status = SManga.ONGOING
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".episodeBox").mapNotNull { element ->
            val episodePage = element.selectFirst(".episode-page") ?: return@mapNotNull null
            val title = element.selectFirst("h4.episodeTitle")?.text() ?: return@mapNotNull null
            val dataTitle = episodePage.attr("data-title").substringBefore("|").trim()

            SChapter.create().apply {
                name = if (dataTitle.isNotEmpty()) "$title - $dataTitle" else title
                chapter_number = title.toFloatOrNull() ?: -1f
                scanlator = "Akita Publishing"

                val dataSrc = episodePage.attr("abs:data-src").ifEmpty { baseUrl + episodePage.attr("data-src") }
                setUrlWithoutDomain(dataSrc.substringBeforeLast("/"))
            }
        }.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.just(
        listOf(
            Page(0, url = chapter.url, imageUrl = "$baseUrl${chapter.url}/image"),
        ),
    )

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", baseUrl + page.url.substringBeforeLast("/"))
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
