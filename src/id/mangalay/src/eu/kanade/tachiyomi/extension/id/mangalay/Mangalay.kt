package eu.kanade.tachiyomi.extension.id.mangalay

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
abstract class Mangalay : KeiSource() {
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/2013/04/daftar-baca-komik_20.html").asJsoup()
        val mangas = document.select(".post-body table").map { element: Element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("a").first()!!.absUrl("href"))
                title = element.select(".tr-caption").text()
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val chapterList = document.select(".post-body span > a").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.select("b").text()
            }
        }
        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".separator img")
            .dropLast(1) // :last-child not working somehow
            .mapIndexed { index: Int, element: Element ->
                Page(index, imageUrl = element.attr("abs:src"))
            }
    }
}
