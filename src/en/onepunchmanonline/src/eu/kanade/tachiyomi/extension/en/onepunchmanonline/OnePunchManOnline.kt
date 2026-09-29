package eu.kanade.tachiyomi.extension.en.onepunchmanonline

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
import okhttp3.Request

@Source
abstract class OnePunchManOnline : KeiSource() {

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(createManga()), false)

    // =============================== Latest ================================
    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = createManga()
        return if (query.isBlank() || manga.title.contains(query, ignoreCase = true)) {
            MangasPage(listOf(manga), false)
        } else {
            MangasPage(emptyList(), false)
        }
    }

    // ============================== Details ===============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val chapterList = if (fetchChapters) getChapters() else chapters
        return SMangaUpdate(createManga(), chapterList)
    }

    private fun createManga(): SManga = SManga.create().apply {
        title = "One Punch Man"
        url = "/"
        thumbnail_url = "https://1punchman.com/wp-content/uploads/2024/02/9782380712018_1_75.jpg"
        author = "ONE"
        artist = "Murata Yusuke"
        status = SManga.ONGOING
        genre = "Action, Comedy, Superhero, Seinen"
        description = "One-Punch Man is a superhero who has trained so hard that his hair has fallen out, and who can overcome any enemy with one punch."
    }

    // ============================== Chapters ===============================
    private suspend fun getChapters(): List<SChapter> = client.get(baseUrl).asJsoup().select("ul li a[href*='/manga/']").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.text()
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("div.entry-content img, .separator img, p img")
            .map { img ->
                img.attr("abs:data-src")
                    .ifEmpty { img.attr("abs:data-lazy-src") }
                    .ifEmpty { img.attr("abs:src") }
            }
            .filter { it.startsWith("http") }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    // Image host (mangafreak) returns 403 when a Referer from this site is sent
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Referer")
        .removeHeader("Origin")
        .build()
}
