package eu.kanade.tachiyomi.multisrc.paprika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class PaprikaAlt(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : Paprika(name, baseUrl, lang) {
    override fun popularMangaSelector() = "div.anipost"

    override fun popularMangaFromElement(element: Element): SManga? {
        val a = element.selectFirst("a:has(h3)") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(a.absUrl("href"))
            title = a.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        GET("$baseUrl/search?s=$query&post_type=manga&page=$page", headers)
    } else {
        val url = "$baseUrl/genres/".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addPathSegment(filter.toUriPart())
                is OrderFilter -> url.addQueryParameter("orderby", filter.toUriPart())
                else -> {}
            }
        }
        url.addQueryParameter("page", page.toString())
        GET(url.build(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".animeinfo .rm h1")!!.text()
        thumbnail_url = document.selectFirst(".animeinfo .lm img")?.attr("abs:src")
        document.select(".listinfo li").forEach { element ->
            with(element.text()) {
                when {
                    startsWith("Author") -> author = substringAfter(":").trim()
                    startsWith("Artist") -> artist = substringAfter(":").trim().replace(";", ",")
                    startsWith("Genre") -> genre = substringAfter(":").trim().replace(";", ",")
                    startsWith("Status") -> status = substringAfter(":").trim().toStatus()
                }
            }
        }
        description = document.select("#noidungm").joinToString("\n") { it.text() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.selectFirst(".animeinfo .rm h1")?.text() ?: ""
        return document.select(chapterListSelector())
            .mapNotNull { chapterFromElement(it, mangaTitle) }
            .distinctBy { it.url }
    }

    override fun chapterListSelector() = ".animeinfo .rm .cl li"

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter? {
        val leftoff = element.selectFirst(".leftoff") ?: return null
        val a = leftoff.selectFirst("a") ?: return null
        return SChapter.create().apply {
            name = leftoff.text().substringAfter("$mangaTitle ")
            setUrlWithoutDomain(a.absUrl("href"))
            date_upload = element.selectFirst(".rightoff")?.text().toDate()
        }
    }
}
