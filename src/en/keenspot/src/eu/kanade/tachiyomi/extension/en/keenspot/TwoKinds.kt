package eu.kanade.tachiyomi.extension.en.keenspot

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
import org.jsoup.nodes.Document
import kotlin.math.min

@Source
abstract class TwoKinds : KeiSource() {

    override val supportsLatest = false

    // the one and only manga entry
    fun mangaSinglePages(): SManga = SManga.create().apply {
        title = "TwoKinds (1 page per chapter)"
        thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
        artist = "Tom Fischbach"
        author = "Tom Fischbach"
        status = SManga.UNKNOWN
        url = "1"
    }

    fun manga20Pages(): SManga = SManga.create().apply {
        title = "TwoKinds (20 pages per chapter)"
        thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
        artist = "Tom Fischbach"
        author = "Tom Fischbach"
        status = SManga.UNKNOWN
        url = "20"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(mangaSinglePages(), manga20Pages()), false)

    // latest Updates not used

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // the manga is one and only, but still write the data again to avoid bugs in backup restore
        val details = if (manga.url == "1") mangaSinglePages() else manga20Pages()

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        return SMangaUpdate(details, chapterListParse(fetchArchive(), manga))
    }

    // chapter list

    private suspend fun fetchArchive(): Document = client.get("$baseUrl/archive/").asJsoup()

    data class TwoKindsPage(val url: String, val name: String)

    private fun parseArchivePages(document: Document): List<TwoKindsPage> = document.select(".chapter-links")
        .flatMap { season -> season.select("> a") }
        .map { a ->
            // /comic/1185halloween/ -> 1185halloween
            val urlPart = a.attr("href").split("/")[2]
            val name = a.selectFirst("span")!!.text()

            TwoKindsPage(urlPart, name)
        }

    private fun chapterListParse(document: Document, manga: SManga): List<SChapter> {
        val pages = parseArchivePages(document)

        // 1 page per chapter
        if (manga.url == "1") {
            return pages.map { page ->
                SChapter.create().apply {
                    url = "1-${page.url}"
                    name = "Page ${page.name}"
                }
            }.reversed()
        }

        // 20 pages per chapter
        val chapters = mutableListOf<SChapter>()
        for (i in pages.indices step 20) {
            chapters.add(
                SChapter.create().apply {
                    url = "20-${pages[i].url}"
                    name = "Pages ${pages[i].name}-${pages[min(pages.size, i + 20) - 1].name}"
                },
            )
        }
        return chapters.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("1")) {
            return listOf(
                Page(0, baseUrl + "/comic/${chapter.url.substringAfter("-")}/"),
            )
        } else {
            val firstPage = chapter.url.substringAfter("-")
            val pages = parseArchivePages(fetchArchive())

            val firstPageIdx = pages.indexOfFirst { it.url == firstPage }
            val lastPageIdx = min(pages.size, firstPageIdx + 20)

            return pages
                .subList(firstPageIdx, lastPageIdx)
                .mapIndexed { idx, page ->
                    Page(idx, baseUrl + "/comic/${page.url}/")
                }
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()

        return document.select("#content article img").first()!!.attr("src")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw Exception("Search functionality is not available.")
}
