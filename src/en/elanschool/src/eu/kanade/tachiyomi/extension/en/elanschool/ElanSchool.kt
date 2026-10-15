package eu.kanade.tachiyomi.extension.en.elanschool

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
import org.jsoup.nodes.Element

@Source
abstract class ElanSchool : KeiSource() {

    override val supportsLatest = false

    private fun mangaInfo(page: Int) = SManga.create().apply {
        url = "/chapters/?dps_paged=$page"
        title = "Elan School"
        thumbnail_url = "$baseUrl/wp-content/uploads/2018/11/The-Elan-School-Comic-1cNEW-1-768x1491.jpg"
        description = "A 16 year old boy named Joe gets indoctrinated into a sick cult that is run by imprisoned teenagers. Based on the true story of the Elan School."
        status = SManga.ONGOING
        author = "Joe Nobody"
        artist = "Joe Nobody"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(mangaInfo(page)), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) mangaInfo(1) else manga
        val chapterList = if (fetchChapters) fetchChapterList(manga) else chapters

        return SMangaUpdate(details, chapterList)
    }

    private fun chapterNextPageSelector() = "a.next"

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val allChaps = mutableListOf<SChapter>()
        var document = client.get(getMangaUrl(manga)).asJsoup()

        while (true) {
            val chapters = document.select(chapterListSelector()).map {
                chapterFromElement(it)
            }
            if (chapters.isEmpty()) {
                break
            }

            allChaps += chapters

            val hasNext = document.select(chapterNextPageSelector()).isNotEmpty()
            if (!hasNext) {
                break
            }

            val nextUrl = document.select(chapterNextPageSelector()).attr("href")
            document = client.get(nextUrl).asJsoup()
        }

        return allChaps.reversed()
    }

    private fun chapterListSelector() = "div.listing-item > a.title"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.attr("href"))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        // Linked images are the language flag, subscribe banner and next-chapter buttons
        return document.select("img[data-orig-file]").filter { it.closest("a") == null }.mapIndexed { i, img ->
            Page(i, "", img.attr("src"))
        }
    }
}
