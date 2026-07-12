package eu.kanade.tachiyomi.extension.ar.neverscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class NeverScans : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("a[href^=/manga/]").mapNotNull { el ->
            val href = el.attr("href")
            val slug = href.removePrefix("/manga/")
            if (slug.isBlank() || slug.contains("/") || slug.contains("?")) return@mapNotNull null
            val img = el.select("img").firstOrNull()
            val title = img?.attr("alt")?.trim()?.ifBlank { null }
                ?: slug.replace("-", " ").replaceFirstChar { it.uppercase() }
            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                thumbnail_url = img?.attr("abs:src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.select("h1").text().ifBlank {
                doc.select("meta[property=og:title]").attr("content")
            }
            description = doc.select("meta[property=og:description], meta[name=description]").attr("content")
            thumbnail_url = doc.select("meta[property=og:image]").attr("content")
            url = response.request.url.encodedPath
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val mangaUrl = response.request.url.encodedPath
        return doc.select("a[href*=$mangaUrl/]").mapNotNull { el ->
            val href = el.attr("href")
            val suffix = href.removePrefix(mangaUrl).trimStart('/')
            if (suffix.isBlank()) return@mapNotNull null
            val raw = Regex("(?:chapter-)?(\\d+)(?:\\.\\d+)?").find(suffix)?.groupValues?.get(1) ?: return@mapNotNull null
            val name = "الفصل $raw"
            SChapter.create().apply {
                url = href.removePrefix("/")
                this.name = name
                chapter_number = raw.toFloatOrNull() ?: 0f
            }
        }.distinctBy { it.chapter_number }
            .sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select("img[src*=/api/public/page/]").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
