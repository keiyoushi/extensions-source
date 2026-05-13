package eu.kanade.tachiyomi.extension.fr.lelscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class Lelscan : HttpSource() {

    override val name = "Lelscan"
    override val baseUrl = "https://lelscans.net"
    override val lang = "fr"
    override val supportsLatest = true

    // A stable reader page guaranteed to carry the navigation dropdowns and latest section.
    private val catalogPage = "$baseUrl/lecture-en-ligne-one-piece"

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(catalogPage, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#navigation select").first()
            ?.select("option")
            ?.map { option ->
                SManga.create().apply {
                    title = option.text()
                    setUrlWithoutDomain(option.attr("abs:value"))
                    thumbnail_url = thumbnailFromPath(url)
                }
            }
            .orEmpty()
        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(catalogPage, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#main_hot_ul li").mapNotNull { li ->
            val a = li.selectFirst("a.hot_manga_img") ?: return@mapNotNull null
            SManga.create().apply {
                title = a.attr("title").removeSuffix(" Scan")
                setUrlWithoutDomain(a.attr("abs:href"))
                thumbnail_url = a.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // Search

    // No server-side search: filter the catalog list client-side.
    // The query is encoded as a URL fragment so it is not sent to the server
    // but is still accessible via response.request.url.fragment.

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$catalogPage#${query.trim()}", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment.orEmpty()
        val document = response.asJsoup()
        val mangas = document.select("#navigation select").first()
            ?.select("option")
            ?.filter { it.text().contains(query, ignoreCase = true) }
            ?.map { option ->
                SManga.create().apply {
                    title = option.text()
                    setUrlWithoutDomain(option.attr("abs:value"))
                    thumbnail_url = thumbnailFromPath(url)
                }
            }
            .orEmpty()
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        // The second breadcrumb div holds "Lecture en ligne {Title}".
        val breadcrumb = document.select("#header-image h2 div").getOrNull(1)
            ?.selectFirst("span[itemprop=title]")?.text().orEmpty()
        return SManga.create().apply {
            title = breadcrumb.removePrefix("Lecture en ligne ")
            thumbnail_url = baseUrl + document.selectFirst("meta[property=og:image]")?.attr("content")
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // The second <select> in #navigation is the chapter dropdown (descending order).
        return document.select("#navigation select").getOrNull(1)
            ?.select("option")
            ?.map { option ->
                val chapterNum = option.text().toFloatOrNull() ?: -1f
                SChapter.create().apply {
                    setUrlWithoutDomain(option.attr("abs:value"))
                    name = "Chapitre ${chapterNum.toString().removeSuffix(".0")}"
                    chapter_number = chapterNum
                    date_upload = 0L
                }
            }
            .orEmpty()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}/1", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#navigation select").getOrNull(2)
            ?.select("option")
            ?.mapIndexed { index, option ->
                Page(index, url = option.attr("abs:value"))
            }
            .orEmpty()
    }

    override fun imageUrlParse(response: Response): String = response.asJsoup().selectFirst("#image img")?.attr("abs:src").orEmpty()

    // Helpers

    // Derives the thumbnail URL from the manga's lecture path.
    // /lecture-en-ligne-one-piece       → /mangas/one-piece/thumb_cover.jpg
    // /lecture-ligne-naruto.php         → /mangas/naruto/thumb_cover.jpg
    private fun thumbnailFromPath(mangaPath: String): String {
        val slug = mangaPath
            .replace(Regex("/lecture-(?:en-ligne|ligne)-"), "")
            .removeSuffix(".php")
        return "$baseUrl/mangas/$slug/thumb_cover.jpg"
    }
}
