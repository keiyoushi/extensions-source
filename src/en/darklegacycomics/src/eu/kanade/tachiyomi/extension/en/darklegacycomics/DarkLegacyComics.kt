package eu.kanade.tachiyomi.extension.en.darklegacycomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class DarkLegacyComics : HttpSource() {
    override val lang = "en"

    override val name = "Dark Legacy Comics"

    override val baseUrl = "https://www.darklegacycomics.com"

    override val supportsLatest = false

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(".archive_link").map {
            val index = it.selectFirst(".index")!!.text()
            val date = it.selectFirst(".date")!!.ownText()
            val title = it.selectFirst(".name")!!.text()
            val characters = it.select(".characters").text()
            SChapter.create().apply {
                url = "/$index"
                name = "#$index: $title"
                chapter_number = index.toFloat()
                // Not actually scanlators but whatever
                scanlator = characters.replace(" ", ", ")
                // One of the dates is missing the year
                date_upload = when (date) {
                    "Sep 20" -> 1442696400000L // Sep 20, 2015
                    else -> dateFormat.parse(date)?.time ?: 0L
                }
            }
        }

    override fun pageListParse(response: Response) =
        response.asJsoup().select(".comic > img").mapIndexed { idx, img ->
            Page(idx, "", img.absUrl("src"))
        }

    override fun fetchPopularManga(page: Int) =
        listOf(
            SManga.create().apply {
                url = "/archive"
                title = "Dark Legacy Comics"
                thumbnail_url = THUMB_URL
                status = SManga.ONGOING
                author = AUTHOR_NAME
                artist = AUTHOR_NAME
            },
            SManga.create().apply {
                url = "/specials/1.php"
                title = "Dark Legacy Comics Specials"
                thumbnail_url = THUMB_URL
                status = SManga.COMPLETED
                author = AUTHOR_NAME
                artist = AUTHOR_NAME
            },
        ).let { Observable.just(MangasPage(it, false))!! }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga) =
        Observable.just(manga.apply { initialized = true })!!

    override fun fetchChapterList(manga: SManga) =
        if (manga.url == "/archive") {
            super.fetchChapterList(manga)
        } else {
            specials.map {
                SChapter.create().apply {
                    name = it.value
                    url = "/specials/${it.key}"
                    chapter_number = it.key.toFloat()
                    date_upload = SPECIALS_DATE
                }
            }.let { Observable.just(it)!! }
        }

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    companion object {
        private const val THUMB_URL = "https://images2.imgbox.com/5d/d8/BVxRdljH_o.png"

        private const val AUTHOR_NAME = "Arad Kedar (Keydar)"

        private const val SPECIALS_DATE = 1399926480000L // 2014-05-12 23:28

        private val specials = mapOf(
            1 to "Looking For Group",
            2 to "Rover",
            3 to "Fan Comic",
        )

        private val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        }
    }
}
