package eu.kanade.tachiyomi.extension.all.commitstrip

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
import java.util.Calendar
import java.util.Locale

@Source
abstract class CommitStrip : KeiSource() {

    override val supportsLatest = false

    private val siteLang: String get() = lang

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)

    // Helper

    private fun createManga(year: Int): SManga = SManga.create().apply {
        url = "$baseUrl/$siteLang/$year"
        title = "$name ($year)"
        thumbnail_url = when (lang) {
            "en" -> LOGO_EN
            "fr" -> LOGO_FR
            else -> LOGO_EN
        }
        author = when (lang) {
            "en" -> AUTHOR_EN
            "fr" -> AUTHOR_FR
            else -> AUTHOR_EN
        }
        artist = ARTIST
        status = if (year != currentYear) SManga.COMPLETED else SManga.ONGOING
        description = when (lang) {
            "en" -> "$SUMMARY_EN $NOTE $year"
            "fr" -> "$SUMMARY_FR $NOTE $year"
            else -> "$SUMMARY_EN $NOTE $year"
        }
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        // have one manga entry for each year
        val mangas = (currentYear downTo 2012).map { createManga(it) }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filtered = getPopularManga(1).mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(filtered, false)
    }

    // Open in WebView

    override fun getMangaUrl(manga: SManga): String = "${manga.url}/?"

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        // years without any strip (e.g. 2023) return 404
        val response = client.get("${manga.url}/?", ensureSuccess = false)
        if (response.code == 404) {
            response.close()
            return SMangaUpdate(manga, emptyList())
        }

        val pages = response.asJsoup().let { document ->
            val responseString = document.selectFirst(".wp-pagenavi .pages")?.text() ?: "1"
            pageRegex.findAll(responseString).lastOrNull()?.value?.toInt() ?: 1
        }

        val chapterList = (1..pages).flatMap { page ->
            client.get("${manga.url}/page/$page/?").asJsoup().select(".excerpt a").map { element ->
                SChapter.create().apply {
                    url = "$baseUrl/$siteLang" + element.attr("href").substringAfter(baseUrl)

                    // get the chapter date from the url
                    val dateStr = dateRegex.find(url)?.value
                    date_upload = dateFormat.tryParseDate(dateStr)

                    name = element.select("span").text()
                }
            }
        }.distinctBy { it.url }

        val total = chapterList.size
        chapterList.forEachIndexed { index, chapter ->
            chapter.chapter_number = (total - index).toFloat()
        }

        return SMangaUpdate(manga, chapterList)
    }

    // Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageUrl = client.get(chapter.url).asJsoup().selectFirst(".entry-content p img")?.attr("abs:src") ?: ""
        return listOf(Page(0, imageUrl = imageUrl))
    }

    companion object {
        private const val LOGO_EN = "https://i.imgur.com/HODJlt9.jpg"
        private const val LOGO_FR = "https://i.imgur.com/I7ps9zS.jpg"
        private const val AUTHOR_EN = "Mark Nightingale"
        private const val AUTHOR_FR = "Thomas Gx"
        private const val ARTIST = "Etienne Issartial"
        private const val SUMMARY_EN = "The blog relating the daily life of web agency developers."
        private const val SUMMARY_FR = "Le blog qui raconte la vie des codeurs"
        private const val NOTE = "\n\nNote: This entry includes all the chapters published in"

        private val dateRegex = Regex("""\d{4}/\d{2}/\d{2}""")
        private val pageRegex = Regex("""\d+""")

        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }
    }
}
