package eu.kanade.tachiyomi.extension.zh.manhuashe

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
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Manhuashe : KeiSource() {

    fun parseManga(document: Document): MangasPage {
        val mangas = document.select("div.comic-list > div.comic-item").map { element: Element ->
            SManga.create().apply {
                title = element.selectFirst("h3 a")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")!!.attr("src")
            }
        }
        val nextPage = document.selectFirst("div.pagination > a.next")!!.attr("href")
        val currentPage = document.selectFirst("div.pagination > a.on")!!.attr("href")
        return MangasPage(mangas, nextPage != currentPage)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/order/hits/page/$page").asJsoup()
        return parseManga(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/order/addtime/page/$page").asJsoup()
        return parseManga(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.get("$baseUrl/search/$query/$page").asJsoup()
        return parseManga(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.encodedPath.startsWith("/comic_")) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        manga.apply {
            title = document.selectFirst("div.comic-meta-info > h1")!!.text()
            thumbnail_url = document.selectFirst("div.comic-cover-large > img")!!.absUrl("src")
            author = document.selectFirst("div.comic-stats > div.stat-item:contains(作者：)")!!.text().removePrefix("作者：")
            genre = document.selectFirst("div.comic-meta-info > div.comic-tags > span")?.text()?.replace(" ", ", ")
            description = document.selectFirst("div.comic-description > p")!!.text()
            status = when (document.select("div.comic-meta-info > div.comic-tags > span").last()?.text()) {
                "连载", "连载中" -> SManga.ONGOING
                "完结", "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        val chapters = document.select("#chapter-list > div.chapter-item > a").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
            }
        }.asReversed()
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select("div.comic-content > img").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("src"))
        }
    }
}
