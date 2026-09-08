package eu.kanade.tachiyomi.extension.en.supermega

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Source
abstract class Supermega : KeiSource() {

    override val supportsLatest = false

    private fun createManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain("/")
        title = "SUPER MEGA"
        artist = "JohnnySmash"
        author = "JohnnySmash"
        status = SManga.ONGOING
        description = ""
        thumbnail_url = "https://www.supermegacomics.com/runningman.png"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val manga = createManga()
        val chapters = if (fetchChapters) {
            val document = client.get(baseUrl).asJsoup()
            val latestComicNumber = document.selectFirst("[name='bigbuttonprevious']")
                ?.parent()?.attr("abs:href")?.toHttpUrlOrNull()?.queryParameter("i")?.toIntOrNull()?.plus(1) ?: 0

            (1..latestComicNumber).reversed().map {
                SChapter.create().apply {
                    name = it.toString()
                    chapter_number = it.toFloat()
                    setUrlWithoutDomain("?i=$it")
                }
            }
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url).asJsoup()
        .select("img[border='4']").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
}
