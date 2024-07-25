package eu.kanade.tachiyomi.extension.en.darthsdroids

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// Dear Darths & Droids creators:
// I’m sorry if this extension causes too much traffic for your site.
// Unfortunately we can’t just download and use your Zip downloads.
// Shall problems arise, we’ll reduce the rate limit.
class DarthsDroids : HttpSource() {
    override val name = "Darths & Droids"
    override val baseUrl = "https://www.darthsanddroids.net"
    override val lang = "en"
    override val supportsLatest = false
    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .build()

    // Picks a thumbnail from the profile pictures of the »cast« pages:
    //   https://www.darthsanddroids.net/cast/
    //
    // Where possible, pick a thumbnail from the corresponding book’s
    // cast page. Try to avoid having a character appear more than once
    // as thumbnail, giving all main characters equal amounts of spotlight.
    // Pick a character people would intuïtively associate with the
    // corresponding film, like Qui-Gon for Phantom Menace or Leia for
    // A New Hope.
    //
    // If a book doesn’t have its own cast page, try source a fitting
    // profile picture from a different page. Avoid sourcing thumbnails
    // from a different website.
    private fun dndThumbnailUrlForTitle(nthManga: Int): String = when (nthManga) {
        // The numbers are assigned in order of appearance of a book on the archive page.
        0 -> "$baseUrl/cast/QuiGon.jpg" // D&D1
        1 -> "$baseUrl/cast/Anakin2.jpg" // D&D2
        2 -> "$baseUrl/cast/ObiWan3.jpg" // D&D3
        3 -> "$baseUrl/cast/JarJar2.jpg" // JJ
        4 -> "$baseUrl/cast/Leia4.jpg" // D&D4
        5 -> "$baseUrl/cast/Han5.jpg" // D&D5
        6 -> "$baseUrl/cast/Luke6.jpg" // D&D6
        7 -> "$baseUrl/cast/Cassian.jpg" // R1
        8 -> "$baseUrl/cast/C3PO4.jpg" // Muppets
        9 -> "$baseUrl/cast/Finn7.jpg" // D&D7
        10 -> "$baseUrl/cast/Han4.jpg" // Solo
        11 -> "$baseUrl/cast/Hux8.jpg" // D&D8
        // Just some nonsense fallback that screams »Star Wars« but is also so recognisably
        // OT that one can understand it’s a mere fallback. Better thumbnails require an
        // extension update.
        else -> "$baseUrl/cast/Vader4.jpg"
    }

    private fun dndManga(archiveUrl: String, mangaTitle: String, mangaStatus: Int, nthManga: Int): SManga = SManga.create().apply {
        setUrlWithoutDomain(archiveUrl)
        thumbnail_url = dndThumbnailUrlForTitle(nthManga)
        title = mangaTitle
        author = "David Morgan-Mar & Co."
        artist = "David Morgan-Mar & Co."
        description = """What if Star Wars as we know it didn't exist, but instead the
            |plot of the movies was being made up on the spot by players of
            |a Tabletop Game?
            |
            |Well, for one, the results might actually make a lot more sense,
            |from an out-of-story point of view…
        """.trimMargin()
        genre = "Campaign Comic, Comedy, Space Opera, Science Fiction"
        status = mangaStatus
        update_strategy = when (mangaStatus) {
            SManga.COMPLETED -> UpdateStrategy.ONLY_FETCH_ONCE
            else -> UpdateStrategy.ALWAYS_UPDATE
        }
        initialized = true
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/archive.html", headers)

    // The book and page archive feeds are rather special for this webcomic.
    // The main archive page `/archive.html` is a combined feed for both,
    // all previous and finished books, as well as all pages of the book that
    // is currently releasing. Every finished book gets its own archive page
    // like `/archive4.html` or `/archiveJJ.html` into which all page links
    // are moved. So whatever book is currently releasing in `/archive.html`
    // will eventually be moved into its own archive, and it’ll instead
    // appear as a book-archive link in `/archive.html`.
    //
    // This means a few things:
    // • The currently releasing book eventually changes its `url`!
    // • The URL of the currently releasing book will be taken over by
    //   whichever new book comes next.
    // • There is no deterministic way of guessing a book’s future
    //   archive name.
    //   ◦ This is especially apparent with the »Solo« book, which’s
    //     archive page is `/solo/`, while all others are `/archiveX.html`.
    //
    // So eventually, Tachiyomi & Co. will glitch out once a currently
    // releasing book finishes. People will find the current book’s page
    // feed to be empty. Even worse, they may find it starting anew with
    // different pages. A manual refresh *should* change the book’s `url`
    // to its new archive page, and all reading progress should be preserved.
    // Then the user will have to manually add the new book to their library.
    //
    // The alternative would be to have a pseudo book »<Title> (ongoing)«
    // that just disappears, being replaced by »<Title>«. But i think that’s
    // even worse in terms of user experience. Maybe one day we’ll have new
    // extension APIs for dealing with unique webcomic weirdnesses. ’cause
    // trust me, there’s worse.
    override fun popularMangaParse(response: Response): MangasPage {
        val mainArchive = response.asJsoup()
        val archiveData = mainArchive.select("div.text > table.text > tbody > tr")

        val mangas = mutableListOf<SManga>()
        var nextMangaTitle = name
        var nthManga = 0

        run stop@{
            archiveData.forEach {
                val maybeTitle = it.selectFirst("th")?.text()
                if (maybeTitle != null) {
                    nextMangaTitle = "$name $maybeTitle"
                } else {
                    val maybeArchive = it.selectFirst("""td[colspan="3"] > a""")?.absUrl("href")
                    if (maybeArchive != null) {
                        mangas.add(dndManga(maybeArchive, nextMangaTitle, SManga.COMPLETED, nthManga++))
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

        return MangasPage(mangas, false)
    }

    // Not efficient, but the simplest way for me to refresh.
    // We also can’t really use the `mangaDetailsRequest + mangaDetailsParse`
    // approach, for we actually expect one of the books’ `url`s to change.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        fetchPopularManga(0)
            .map { mangasPage ->
                mangasPage
                    .mangas
                    // Do not test for URL-equality, for the last book will always
                    // eventually migrate its archive page from `/archive.html` to
                    // its own page.
                    .first { it.title == manga.title }
            }

    // This implementation here is needlessly complicated, for it has to automatically detect
    // whether we’re in a date-annotated archive, the main archive, or a dateless archive.
    // All three are largely similar, there are just *some* (annoying) differences we have to
    // deal with.
    override fun chapterListParse(response: Response): List<SChapter> {
        val archivePages = response.asJsoup()

        // For books where all pages released the same day, there is no page date column,
        // so instead we grab the release date of the archive page itself from its footer.
        val pageDate = archivePages
            .select("""br + i""")
            .mapNotNull { EXTR_PAGE_DATE.find(it.text())?.groupValues?.getOrNull(1) }
            .map { PAGE_DATE_FMT.parse(it)?.time }
            .firstOrNull()
            ?: 0L
        var i = 0

        return archivePages
            .select("""div.text > table.text > tbody > tr""")
            .mapNotNull {
                val pageData = it.select("""td""")
                var pageAnchor = pageData.getOrNull(2)?.selectFirst("a")
                // null for »Intermission«, main archive, dateless archive,…
                if (pageAnchor != null) {
                    SChapter.create().apply {
                        name = pageAnchor!!.text()
                        chapter_number = (i++).toFloat()
                        date_upload = runCatching {
                            DATE_FMT.parse(pageData[0].text())!!.time
                        }.getOrDefault(0L)
                        setUrlWithoutDomain(pageAnchor!!.absUrl("href"))
                    }
                } else if (!pageData.hasAttr("colspan")) {
                    // Are we in a dateless archive?
                    pageAnchor = pageData.getOrNull(0)?.selectFirst("a")
                    if (pageAnchor != null) {
                        SChapter.create().apply {
                            name = pageAnchor.text()
                            chapter_number = (i++).toFloat()
                            date_upload = pageDate
                            setUrlWithoutDomain(pageAnchor.absUrl("href"))
                        }
                    } else { null }
                } else { null }
            }
            .reversed()
    }

    override fun pageListParse(response: Response): List<Page> =
        // Careful. For almost all images it’s `div.center>p>img`, except for pages released on
        // April’s Fools day, when it’s `div.center>p>a>img`. We could still add the `p` in
        // between, but it was decided to leave it out, in case yet another *almost* same
        // page layout pops up in the future.
        //
        // For example, this episode was released during April’s Fools day.
        // https://www.darthsanddroids.net/episodes/0082.html
        response
            .asJsoup()
            .select("""div.center img""")
            .mapIndexed { i, img ->
                Page(
                    index = i,
                    imageUrl = img.absUrl("src"),
                )
            }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FMT = SimpleDateFormat("EEE d MMM, yyyy", Locale.US)
        private val EXTR_PAGE_DATE = """Published\:\s+(\w+,\s+\d+\s+\w+,\s+\d+\;\s+\d+\:\d+\:\d+\s+\w+)""".toRegex()
        private val PAGE_DATE_FMT = SimpleDateFormat("EEEEE, d MMMMM, yyyy; HH:mm:ss zzz", Locale.US)
    }
}
