package eu.kanade.tachiyomi.extension.ar.hentaiman

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.format.DateTimeFormatter

@Source
abstract class HentaiMan : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val dateFormat = DateTimeFormatter.ofPattern("d/M/yy")

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/manga?page=$page"))

    private fun parseMangaList(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("#manga-grid > div").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null
            val img = card.selectFirst("img[src*=storage/covers]")
            val imgSrc = img?.attr("abs:src")?.takeIf { it.isNotEmpty() }
                ?: img?.attr("abs:data-src")

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = imgSrc
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/?page=$page"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search", query)
            .build()
        return parseMangaList(client.get(url))
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val mangaPath = response.request.url.encodedPath
        val doc = response.asJsoup()

        val updatedManga = manga.apply {
            title = doc.selectFirst("h1")?.text()!!
            thumbnail_url = doc.selectFirst("img[src*=storage/covers/lg], img[src*=storage/covers/md]")
                ?.attr("abs:src")
            description = doc.select("[aria-label=Alternative Title]").text().ifEmpty {
                doc.selectFirst("dl dd")?.text()
            }
            genre = doc.select("a[href*=list/genre]").joinToString { it.text() }
            status = when {
                doc.select("span.status-completed").isNotEmpty() -> SManga.COMPLETED
                doc.select("span.status-on-going").isNotEmpty() -> SManga.ONGOING
                doc.select("span.status-hiatus").isNotEmpty() -> SManga.ON_HIATUS
                doc.select("span.status-canceled").isNotEmpty() -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = doc.select("li.chapter-item").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            val chapterPath = href.removePrefix("$baseUrl$mangaPath/")
            val chapterNum = chapterPath.trim('/').split("/").lastOrNull()?.toFloatOrNull()
            val chapterName = link.selectFirst("span:not([class])")?.text() ?: ""
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = "الفصل ${chapterNum?.toInt() ?: "?"} - $chapterName"
                chapter_number = chapterNum ?: 0f
                date_upload = dateFormat.tryParseDate(link.selectFirst("p.text-gray-400")?.text())
            }
        }.sortedByDescending { it.chapter_number }.distinctBy { it.url }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        return doc.select("#reader img.reader-page").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(i, imageUrl = src)
        }
    }
}
