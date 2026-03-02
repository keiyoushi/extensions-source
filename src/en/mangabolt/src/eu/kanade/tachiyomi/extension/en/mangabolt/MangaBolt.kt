package eu.kanade.tachiyomi.extension.en.mangabolt

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MangaBolt : HttpSource() {

    override val name = "MangaBolt"

    override val baseUrl = "https://mangabolt.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    // Popular
    // Site doesn't have thumbnails for these so they Will only load after opening details
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/storage/manga-list.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".section-header, .menu-item").asSequence().mapNotNull { element ->
            val onclick = element.attr("onclick")
            val path = onclick.substringAfter("'", "").substringBefore("'", "")
            if (path.isEmpty()) return@mapNotNull null
            SManga.create().apply {
                url = "${path.removeSuffix("/")}/"
                title = element.select("h2, .item-title").text().removeSuffix("🔥").trim()
                if (title.isEmpty()) return@mapNotNull null
            }
        }.toList()
        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.bg-bg-secondary:has(a[href*=/chapter/])").asSequence().mapNotNull { element ->
            val link = element.selectFirst("a[href*=/chapter/]")?.attr("href") ?: return@mapNotNull null

            val slug = link.substringAfter("/chapter/", "").substringBefore("-chapter-", "")
            if (slug.isEmpty()) return@mapNotNull null

            SManga.create().apply {
                url = "/manga/$slug/"
                title = element.select(".font-bold").text().substringBefore("Chapter").trim()
                if (title.isEmpty()) return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }.toList()

        return MangasPage(mangas, false)
    }

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(popularMangaRequest(page))
        .asObservableSuccess()
        .map { response ->
            val allManga = popularMangaParse(response).mangas
            val filtered = allManga.filter { it.title.contains(query, ignoreCase = true) }
            MangasPage(filtered, false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("#main-content h1")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: throw Exception("Missing title")
            description = document.select("div.bg-bg-secondary div.px-6 div.flex-col div.text-text-muted").text().trim()
            thumbnail_url = document.selectFirst("div.flex img")?.attr("abs:src")
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.w-full div.bg-bg-secondary:has(div.grid)").mapNotNull { element ->
            val link = element.selectFirst("div.grid a") ?: return@mapNotNull null
            SChapter.create().apply {
                name = link.text()
                val secondaryTitle = link.parent()?.selectFirst(".text-xs")?.text()?.takeIf { !it.equals("READ", ignoreCase = true) } ?: ""
                if (secondaryTitle.isNotEmpty()) {
                    name += " - $secondaryTitle"
                }
                url = link.attr("abs:href")
            }
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".js-pages-container img.js-page").asSequence()
            .filter { !it.parents().any { parent -> parent.tagName() == "noscript" } }
            .map { img ->
                if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
            }
            .filter { it.isNotEmpty() && !it.contains("data:image") }
            .distinct()
            .mapIndexed { index, url -> Page(index, "", url) }
            .toList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
