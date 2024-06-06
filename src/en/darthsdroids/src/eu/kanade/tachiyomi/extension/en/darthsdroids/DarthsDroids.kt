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

    private val seriesAuthors = "David Morgan-Mar & Co."
    private val seriesGenres = "Campaign Comic, Comedy, Space Opera, Science Fiction"
    private val seriesDescription = "" +
        // Quoted from TV Tropes.
        "What if Star Wars as we know it didn't exist, but instead the " +
        "plot of the movies was being made up on the spot by players of " +
        "a Tabletop Game?\n" +
        "Well, for one, the results might actually make a lot more sense, " +
        "from an out-of-story point of view…"

    private val titleDnd1 = "$name I. The Phantasmal Malevolence"
    private val titleDnd2 = "$name II. The Silence of the Clones"
    private val titleDnd3 = "$name III. Revelation of the Sith"
    private val titleDndJJ = "$name The Ballad of Jar Jar"
    private val titleDnd4 = "$name IV. A New Generation"
    private val titleDnd5 = "$name V. The Enemy Let Slip"
    private val titleDnd6 = "$name VI. The Jedi Reloaded"
    private val titleDndR1 = "$name Butch Cassian and the Sundance Droid"
    private val titleDndMuppets = "$name The Invisible Hands"
    private val titleDnd7 = "$name VII. The Forced-Away Kin"

    // TODO parse Solo feed separately
    private val titleDndSolo = "$name One Rogue, Many Identities - Greedo’s Awesome Epic Backstory"
    private val titleDnd8 = "$name VIII. The Jedi List"

    private fun dnd1(): SManga = SManga.create().apply {
        url = "$baseUrl/archive1.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/QuiGon.jpg"
        title = titleDnd1
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd2(): SManga = SManga.create().apply {
        url = "$baseUrl/archive2.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Anakin2.jpg"
        title = titleDnd2
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd3(): SManga = SManga.create().apply {
        url = "$baseUrl/archive3.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/ObiWan3.jpg"
        title = titleDnd3
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dndJJ(): SManga = SManga.create().apply {
        url = "$baseUrl/archiveJJ.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/JarJar2.jpg"
        title = titleDndJJ
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd4(): SManga = SManga.create().apply {
        url = "$baseUrl/archive4.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Leia4.jpg"
        title = titleDnd4
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd5(): SManga = SManga.create().apply {
        url = "$baseUrl/archive5.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Han5.jpg"
        title = titleDnd5
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd6(): SManga = SManga.create().apply {
        url = "$baseUrl/archive6.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/DarthVader6.jpg"
        title = titleDnd6
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dndR1(): SManga = SManga.create().apply {
        url = "$baseUrl/archiveR1.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Cassian.jpg"
        title = titleDndR1
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dndMuppets(): SManga = SManga.create().apply {
        url = "$baseUrl/archiveMuppets.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/C3PO4.jpg"
        title = titleDndMuppets
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd7(): SManga = SManga.create().apply {
        url = "$baseUrl/archive7.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Finn7.jpg"
        title = titleDnd7
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun dnd8(): SManga = SManga.create().apply {
        url = "$baseUrl/archive8.html"
        thumbnail_url = "https://www.darthsanddroids.net/cast/Hux8.jpg"
        title = titleDnd8
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
        status = SManga.ONGOING
        update_strategy = UpdateStrategy.ALWAYS_UPDATE
        initialized = true
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(
                dnd1(), dnd2(), dnd3(),
                dndJJ(),
                dnd4(), dnd5(), dnd6(),
                dndR1(), dndMuppets(),
                dnd7(), dnd8(),
            ),
            false,
        ),
    )

    // Have the user manually fetch data again on a backup restore. Branching on
    // titles is rather un-fun.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var archiveResponse = client.newCall(GET(manga.url, headers)).execute()
        if (!archiveResponse.isSuccessful) {
            archiveResponse = client.newCall(GET(baseUrl + "/archive.html", headers)).execute()
        }
        val archivePages = archiveResponse.asJsoup()

        val chapters = mutableListOf<SChapter>()
        var i = 1

        archivePages.select("""div.text > table.text > tbody > tr""").forEach {
            val pageData = it.select("""td""")
            val pageAnchor = pageData.getOrNull(2)?.children()?.first()
            // Can be null in cases where section headings like »Intermission« are injected.
            // Can also be null if we’re parsing the base archive page, because for whatever
            // reason there is no extra page for the currently running comic. Instead it’s
            // inlined into the base archive.
            if (pageAnchor != null) {
                val pageDate = DATE_FMT.parse(pageData[0].text().trim())?.time ?: 0L
                val pageLink = pageAnchor.attr("href")
                val pageTitle = pageAnchor.text()
                val pageNum = i.toFloat()

                chapters.add(
                    SChapter.create().apply {
                        name = pageTitle
                        chapter_number = pageNum
                        date_upload = pageDate
                        url = pageLink
                    },
                )

                i += 1
            }
        }

        chapters.reverse()

        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.just(listOf(Page(0, chapter.url)))

    override fun fetchImageUrl(page: Page): Observable<String> {
        val comicPage = client.newCall(GET(baseUrl + page.url, headers)).execute().asJsoup()
        val imageUrl = comicPage.selectFirst("div.center > p > img")!!.attr("src")
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
