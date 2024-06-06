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
    private val titleDndSolo = "$name One Rogue, Many Identities - Greedo’s Awesome Epic Backstory"
    private val titleDnd8 = "$name VIII. The Jedi List"

    private fun dndThumbnailUrlForTitle(mangaTitle: String): String = when (mangaTitle) {
        titleDnd1 -> "https://www.darthsanddroids.net/cast/QuiGon.jpg"
        titleDnd2 -> "https://www.darthsanddroids.net/cast/Anakin2.jpg"
        titleDnd3 -> "https://www.darthsanddroids.net/cast/ObiWan3.jpg"
        titleDndJJ -> "https://www.darthsanddroids.net/cast/JarJar2.jpg"
        titleDnd4 -> "https://www.darthsanddroids.net/cast/Leia4.jpg"
        titleDnd5 -> "https://www.darthsanddroids.net/cast/Han5.jpg"
        titleDnd6 -> "https://www.darthsanddroids.net/cast/Luke6.jpg"
        titleDndR1 -> "https://www.darthsanddroids.net/cast/Cassian.jpg"
        titleDndMuppets -> "https://www.darthsanddroids.net/cast/C3PO4.jpg"
        titleDnd7 -> "https://www.darthsanddroids.net/cast/Finn7.jpg"
        titleDndSolo -> "https://www.darthsanddroids.net/cast/Han4.jpg"
        titleDnd8 -> "https://www.darthsanddroids.net/cast/Hux8.jpg"
        // Just some nonsense fallback that screams »Star Wars« but is also so recognisably
        // OT that one can understand it’s a mere fallback. Better thumbnails require an
        // extension update.
        else -> "https://www.darthsanddroids.net/cast/Vader4.jpg"
    }

    private fun dndManga(archiveUrl: String, mangaTitle: String, mangaStatus: Int): SManga = SManga.create().apply {
        url = archiveUrl
        thumbnail_url = dndThumbnailUrlForTitle(mangaTitle)
        title = mangaTitle
        author = seriesAuthors
        artist = seriesAuthors
        description = seriesDescription
        genre = seriesGenres
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

        run stop@{
            archiveData.forEach {
                val maybeTitle = it.selectFirst("th")?.text()
                if (maybeTitle != null) {
                    nextMangaTitle = "$name $maybeTitle"
                } else {
                    val maybeArchive = it.selectFirst("""td[colspan="3"] > a""")?.attr("href")
                    if (maybeArchive != null) {
                        mangas.add(dndManga(baseUrl + maybeArchive, nextMangaTitle, SManga.COMPLETED))
                    } else {
                        // We reached the end, assuming the page layout stays consistent beyond D&D8.
                        // Thus, we append our final manga with this current page as its archive.
                        // Unfortunately this means we will needlessly fetch this page twice.
                        mangas.add(dndManga(mainArchiveUrl, nextMangaTitle, SManga.ONGOING))
                        return@stop
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas, false))
    }

    // Have the user manually fetch data again on a backup restore. Branching on
    // titles is rather un-fun.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    // This implementation here is needlessly complicated, for it has to automatically detect
    // whether we’re in a date-annotated archive, the main archive, or a dateless archive.
    // All three are largely similar, there are just *some* (annoying) differences we have to
    // deal with.
    private fun fetchChaptersForDatedAndDatelessArchives(archivePages: Document): List<SChapter> {
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
                        url = a.attr("href")
                    },
                )
                i += 1
            } else if (!pageData.hasAttr("colspan")) {
                // Are we in a date-less archive?
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
                            url = a.attr("href")
                        },
                    )
                    i += 1
                }
            }
        }
        chapters.reverse()
        return chapters
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var archiveResponse = client.newCall(GET(manga.url, headers)).execute()
        if (!archiveResponse.isSuccessful) {
            archiveResponse = client.newCall(GET("$baseUrl/archive.html", headers)).execute()
        }

        // Fingers crossed they don’t come up with yet another different archive structure.
        return Observable.just(fetchChaptersForDatedAndDatelessArchives(archiveResponse.asJsoup()))
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
