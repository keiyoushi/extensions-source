package eu.kanade.tachiyomi.extension.en.schlockmercenary

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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Source
abstract class Schlockmercenary : KeiSource() {

    override val supportsLatest = false

    // Books

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl$ARCHIVE_URL").asJsoup()
        val mangas = document.select("div.archive-book").map { element ->
            val book = element.selectFirst("h4 > a")!!
            val thumbUrl = element.selectFirst("img")?.attr("abs:src") ?: "$baseUrl$DEFAULT_THUMBNAIL_URL"
            SManga.create().apply {
                url = book.attr("href")
                title = book.text()
                artist = "Howard Tayler"
                author = "Howard Tayler"
                // Schlock Mercenary finished as of July 2020
                status = SManga.COMPLETED
                description = element.selectFirst("p")?.text() ?: ""
                thumbnail_url = thumbUrl.substringBefore("?")
            }
        }
        return MangasPage(mangas, false)
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get("$baseUrl$ARCHIVE_URL").asJsoup()
        val sanitizedTitle = manga.title.replace(TITLE_SANITIZATION_REGEX, "\\\\$1")
        val book = document.select("div.archive-book:contains($sanitizedTitle)")

        val chapterList = book.select("ul.chapters > li:not(ul > li > ul > li) > a").mapIndexed { index, element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()
                chapter_number = (index + 1).toFloat()
                date_upload = dateFormat.tryParseDate(url.takeLast(10))
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl$ARCHIVE_URL").asJsoup()

        val currentChapter = document.selectFirst("ul.chapters > li:not(ul > li > ul > li) > a[href=${chapter.url}]")
            ?: return emptyList()

        val start = LocalDate.parse(currentChapter.attr("href").takeLast(10), dateFormat)
        var nextChapter = currentChapter.parent()?.nextElementSibling()?.selectFirst("a")

        if (nextChapter == null) {
            nextChapter = currentChapter.parents()[2]?.nextElementSibling()?.selectFirst("ul.chapters > li:not(ul > li > ul > li) > a")
        }

        val end = nextChapter?.let { LocalDate.parse(it.attr("href").takeLast(10), dateFormat) }
            ?: start.plusDays(1)

        return generatePageListBetweenDates(start, end)
    }

    private suspend fun generatePageListBetweenDates(start: LocalDate, end: LocalDate): List<Page> {
        val pages = mutableListOf<Page>()
        var day = start

        while (day.isBefore(end)) {
            getImageUrlsForDay(day.format(dateFormat)).forEach {
                pages.add(Page(pages.size, imageUrl = it))
            }
            day = day.plusDays(1)
        }

        return pages
    }

    private suspend fun getImageUrlsForDay(day: String): List<String> {
        val document = client.get("$baseUrl/$day").asJsoup()
        return document.select("div#strip-$day > img").map { it.attr("abs:src") }
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    companion object {
        const val DEFAULT_THUMBNAIL_URL = "/static/img/logo.b6dacbb8.jpg"
        const val ARCHIVE_URL = "/archives/"

        private val TITLE_SANITIZATION_REGEX = """([",'])""".toRegex()
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
