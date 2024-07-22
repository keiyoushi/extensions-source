package eu.kanade.tachiyomi.extension.en.darthsdroids

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

// Dear Darths & Droids creators:
// I’m sorry if this extension causes too much traffic for your site.
// If possible, i would use the Zip downloads to source finished
// chapters. However, as far as i can see, there is no way to hack
// this into the existing extension API.
class DarthsDroids : HttpSource() {
    override val name = "Darths & Droids"
    override val baseUrl = "https://www.darthsanddroids.net/"
    override val lang = "en"
    override val supportsLatest = false

    private val mainArchiveUrl = "$baseUrl/archive.html"

    private fun dndThumbnailUrlForTitle(nthManga: Int): String = when (nthManga) {
        0 -> "https://www.darthsanddroids.net/cast/QuiGon.jpg" // D&D1
        1 -> "https://www.darthsanddroids.net/cast/Anakin2.jpg" // D&D2
        2 -> "https://www.darthsanddroids.net/cast/ObiWan3.jpg" // D&D3
        3 -> "https://www.darthsanddroids.net/cast/JarJar2.jpg" // JJ
        4 -> "https://www.darthsanddroids.net/cast/Leia4.jpg" // D&D4
        5 -> "https://www.darthsanddroids.net/cast/Han5.jpg" // D&D5
        6 -> "https://www.darthsanddroids.net/cast/Luke6.jpg" // D&D6
        7 -> "https://www.darthsanddroids.net/cast/Cassian.jpg" // R1
        8 -> "https://www.darthsanddroids.net/cast/C3PO4.jpg" // Muppets
        9 -> "https://www.darthsanddroids.net/cast/Finn7.jpg" // D&D7
        10 -> "https://www.darthsanddroids.net/cast/Han4.jpg" // Solo
        11 -> "https://www.darthsanddroids.net/cast/Hux8.jpg" // D&D8
        // Just some nonsense fallback that screams »Star Wars« but is also so recognisably
        // OT that one can understand it’s a mere fallback. Better thumbnails require an
        // extension update.
        else -> "https://www.darthsanddroids.net/cast/Vader4.jpg"
    }

    private fun dndManga(archiveUrl: String, mangaTitle: String, mangaStatus: Int, nthManga: Int): SManga = SManga.create().apply {
        url = archiveUrl
        thumbnail_url = dndThumbnailUrlForTitle(nthManga)
        title = mangaTitle
        author = "David Morgan-Mar & Co."
        artist = "David Morgan-Mar & Co."
        description = """What if Star Wars as we know it didn't exist, but instead the
            | plot of the movies was being made up on the spot by players of
            | a Tabletop Game?\n
            | Well, for one, the results might actually make a lot more sense,
            | from an out-of-story point of view…
        """.trimMargin()
        genre = "Campaign Comic, Comedy, Space Opera, Science Fiction"
        status = mangaStatus
        update_strategy = when (mangaStatus) {
            SManga.COMPLETED -> UpdateStrategy.ONLY_FETCH_ONCE
            else -> UpdateStrategy.ALWAYS_UPDATE
        }
        initialized = true
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val mainArchive = client.newCall(GET(mainArchiveUrl, headers)).execute().asJsoup()
        val archiveData = mainArchive.select("div.text > table.text > tbody > tr")

        val mangas = mutableListOf<SManga>()
        var nextMangaTitle = name
        var nthManga = 0

        run stop@{
            archiveData.forEach {
                val maybeTitle = it.selectFirst("th")?.text()?.trim()
                if (maybeTitle != null) {
                    nextMangaTitle = "$name $maybeTitle"
                } else {
                    val maybeArchive = it.selectFirst("""td[colspan="3"] > a""")?.attr("href")
                    if (maybeArchive != null) {
                        mangas.add(dndManga(maybeArchive, nextMangaTitle, SManga.COMPLETED, nthManga))
                        nthManga += 1
                    } else {
                        // We reached the end, assuming the page layout stays consistent beyond D&D8.
                        // Thus, we append our final manga with this current page as its archive.
                        // Unfortunately this means we will needlessly fetch this page twice.
                        mangas.add(dndManga("/archive.html", nextMangaTitle, SManga.ONGOING, nthManga))
                        return@stop
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas, false))
    }

    // Not efficient, but the simplest way for me to refresh
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        fetchPopularManga(0)
            .map { mangasPage ->
                mangasPage
                    .mangas
                    .first { it.url == manga.url }!!
            }

    // This implementation here is needlessly complicated, for it has to automatically detect
    // whether we’re in a date-annotated archive, the main archive, or a dateless archive.
    // All three are largely similar, there are just *some* (annoying) differences we have to
    // deal with.
    private fun fetchChaptersForDatedAndDatelessArchives(archivePages: Document, archivePath: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var i = 0
        archivePages.select("""div.text > table.text > tbody > tr""").forEach {
            val pageData = it.select("""td""")
            var pageAnchor = pageData.getOrNull(2)?.selectFirst("a")
            // null for »Intermission«, main archive, dateless archive,…
            if (pageAnchor != null) {
                val a = pageAnchor

                chapters.add(
                    SChapter.create().apply {
                        name = a.text()
                        chapter_number = i.toFloat()
                        date_upload = DATE_FMT.parse(pageData[0].text().trim())?.time ?: 0L
                        url = absolutePageUrl(a.attr("href"), archivePath)
                    },
                )
                i += 1
            } else if (!pageData.hasAttr("colspan")) {
                // Are we in a dateless archive?
                pageAnchor = pageData.getOrNull(0)?.selectFirst("a")
                if (pageAnchor != null) {
                    // For now we assume all dateless archives were published the same day
                    // the Solo book was published, because currently Solo’s the only one such book.
                    val pageDate = 1660435200000L + (i * 1000 * 60)
                    val a = pageAnchor

                    chapters.add(
                        SChapter.create().apply {
                            name = a.text()
                            chapter_number = i.toFloat()
                            date_upload = pageDate
                            url = absolutePageUrl(a.attr("href"), archivePath)
                        },
                    )
                    i += 1
                }
            }
        }
        chapters.reverse()
        return chapters
    }

    private fun absolutePageUrl(pageUrl: String, maybeRelativeTo: String): String =
        if (pageUrl.startsWith('/')) {
            pageUrl
        } else {
            // This happens because for whatever reason, the `solo` index isn’t
            // a `.html` page, but a directory path with an `index.html`. So
            // we’re returning `solo/$pageUrl`.
            maybeRelativeTo + pageUrl
        }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var archiveResponse = client.newCall(GET(baseUrl + manga.url, headers)).execute()
        if (!archiveResponse.isSuccessful) {
            archiveResponse = client.newCall(GET(mainArchiveUrl, headers)).execute()
        }

        // Fingers crossed they don’t come up with yet another different archive structure.
        // Technically `manga.url` is »incorrect« for the main archive, but that parameter
        // is ignored for almost all archives anyway. Only those not pointing at a `.html`
        // page matter.
        return Observable.just(fetchChaptersForDatedAndDatelessArchives(archiveResponse.asJsoup(), manga.url))
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        Observable.just(listOf(Page(0, chapter.url)))

    override fun fetchImageUrl(page: Page): Observable<String> {
        val comicPage = client.newCall(GET(baseUrl + page.url, headers)).execute().asJsoup()
        // Careful. For almost all images it’s `div.center>p>img`, except for pages released on
        // April’s Fools day, when it’s `div.center>p>a>img`.
        val imageUrl = comicPage.selectFirst("div.center img")!!.attr("src")
        return Observable.just(baseUrl + imageUrl)
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FMT = SimpleDateFormat("EEE d MMM, yyyy", Locale.US)
    }
}
