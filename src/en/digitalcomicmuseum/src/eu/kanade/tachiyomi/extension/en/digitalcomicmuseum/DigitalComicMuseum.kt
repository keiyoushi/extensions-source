package eu.kanade.tachiyomi.extension.en.digitalcomicmuseum

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody

@Source
abstract class DigitalComicMuseum : KeiSource() {

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/stats.php?ACT=latest&start=${page - 1}00&limit=100").asJsoup()
        val mangas = document.select("tbody > .mainrow").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
            }
        }
        val hasNextPage = document.selectFirst("img[alt=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/stats.php?ACT=topdl&start=${page - 1}00&limit=100").asJsoup()
        val mangas = document.select("tbody > .mainrow").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
            }
        }
        val hasNextPage = document.selectFirst("img[alt=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("terms", query)
            .build()
        val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("ACT", "dosearch")
            .build()
        val document = client.post(url, requestBody).asJsoup()
        val mangas = document.select("#search-results tbody > tr").map { element ->
            SManga.create().apply {
                val baseElement = element.selectFirst("td > a")!!
                setUrlWithoutDomain(baseElement.attr("abs:href"))
                title = baseElement.text()
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
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val elements = document.select(".tableborder")
        elements.first()?.let { firstElement ->
            manga.title = firstElement.select("#catname").text()
            manga.thumbnail_url = firstElement.selectFirst("table img")?.attr("abs:src")

            elements.forEach {
                when (it.selectFirst("#catname")?.text()) {
                    "Description" -> manga.description = it.selectFirst("table")?.text()
                }
            }
        }

        val chapterList = elements.take(1).map { element ->
            SChapter.create().apply {
                name = element.select("#catname").text()
                setUrlWithoutDomain(element.selectFirst(".tablefooter a:first-of-type")!!.attr("abs:href"))
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".reader-page-list a.reader-page-link").mapIndexed { index, element ->
            Page(index, url = element.attr("abs:href"))
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        return document.selectFirst("img.reader-page-image")!!.absUrl("src")
    }
}
