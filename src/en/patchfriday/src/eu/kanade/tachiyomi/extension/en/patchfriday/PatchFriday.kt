package eu.kanade.tachiyomi.extension.en.patchfriday

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

@Source
abstract class PatchFriday : KeiSource() {

    override val supportsLatest = false

    private fun createManga(): SManga = SManga.create().apply {
        initialized = true
        title = "Patch Friday"
        status = SManga.ONGOING
        url = ""
        author = "Patch Friday"
        artist = author
        thumbnail_url = "https://patchfriday.com/patches/68.png"
        description = "The IT security webcomic"
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // Details + Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) createManga() else manga
        val chapterList = if (fetchChapters) fetchChapterList() else chapters

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchChapterList(): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = client.get("$baseUrl/search/?search=").asJsoup()
        var page = document.select("div > div:first-of-type > div:first-of-type > a").attr("abs:href").replace(baseUrl, "").replace("/", "").trim().toInt()
        while (page > 0) {
            val element = document.select("div > div > div:first-of-type > a")
            element.forEach {
                val chapter = SChapter.create()
                chapter.url = it.attr("abs:href").replace(baseUrl, "").trim()
                chapter.chapter_number = chapter.url.replace("/", "").trim().toFloat()
                chapter.name = "#${chapter.chapter_number.toInt()} - ${it.text()}"
                chapter.date_upload = System.currentTimeMillis()
                chapters.add(chapter)
            }
            page -= 10
            document = client.get("$baseUrl/search/?search=&id=$page").asJsoup()
        }
        // Add First Chapter becouse for some reason it does not show up in chapter search
        chapters.add(
            SChapter.create().apply {
                url = "/1/"
                chapter_number = url.replace("/", "").trim().toFloat()
                name = "#${chapter_number.toInt()} - The One"
                date_upload = System.currentTimeMillis()
            },
        )
        return chapters
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(Page(0, baseUrl + chapter.url))

    override suspend fun getImageUrl(page: Page): String = client.get(page.url).asJsoup().select("div#strip_image img").attr("abs:src")
}
