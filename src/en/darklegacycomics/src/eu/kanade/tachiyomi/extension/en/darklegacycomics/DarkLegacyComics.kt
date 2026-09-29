package eu.kanade.tachiyomi.extension.en.darklegacycomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DarkLegacyComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(
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
        ),
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getPopularManga(page)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val updatedChapters = if (manga.url == "/archive") {
            // The archive lists every comic twice (ascending and descending) for its JS reorder toggle
            client.get(baseUrl + manga.url).asJsoup().select(".archive_link").map {
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
                        "Sep 20" -> 1442696400000L

                        // Sep 20, 2015
                        else -> dateFormat.tryParseDate(date)
                    }
                }
            }.distinctBy { it.url }.sortedByDescending { it.chapter_number }
        } else {
            specials.map {
                SChapter.create().apply {
                    name = it.value
                    url = "/specials/${it.key}"
                    chapter_number = it.key.toFloat()
                    date_upload = SPECIALS_DATE
                }
            }.asReversed()
        }

        return SMangaUpdate(manga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter) = client.get(baseUrl + chapter.url).asJsoup().select(".comic > img").mapIndexed { idx, img ->
        Page(idx, imageUrl = img.absUrl("src"))
    }

    companion object {
        private const val THUMB_URL = "https://images2.imgbox.com/5d/d8/BVxRdljH_o.png"

        private const val AUTHOR_NAME = "Arad Kedar (Keydar)"

        private const val SPECIALS_DATE = 1399926480000L // 2014-05-12 23:28

        private val specials = mapOf(
            1 to "Looking For Group",
            2 to "Rover",
            3 to "Fan Comic",
        )

        private val dateFormat = DateTimeFormatter.ofPattern("[MMMM][MMM] d, yyyy", Locale.ENGLISH)
    }
}
