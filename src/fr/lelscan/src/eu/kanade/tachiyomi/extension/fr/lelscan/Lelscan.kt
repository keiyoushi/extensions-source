package eu.kanade.tachiyomi.extension.fr.lelscan

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
import org.jsoup.nodes.Document

@Source
abstract class Lelscan : KeiSource() {

    // A stable reader page guaranteed to carry the navigation dropdowns and latest section.
    private val catalogPage get() = "$baseUrl/lecture-en-ligne-one-piece"

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(catalogPage).asJsoup()
        return MangasPage(document.catalogMangas(), false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(catalogPage).asJsoup()
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
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.get(catalogPage).asJsoup()
        val mangas = document.catalogMangas().filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    private fun Document.catalogMangas(): List<SManga> = select("#navigation select").first()
        ?.select("option")
        ?.map { option ->
            SManga.create().apply {
                title = option.text()
                setUrlWithoutDomain(option.attr("abs:value"))
                thumbnail_url = thumbnailFromPath(url)
            }
        }
        .orEmpty()

    // Details & chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        // The second breadcrumb div holds "Lecture en ligne {Title}".
        val breadcrumb = document.select("#header-image h2 div").getOrNull(1)
            ?.selectFirst("span[itemprop=title]")?.text().orEmpty()
        val updatedManga = manga.apply {
            title = breadcrumb.removePrefix("Lecture en ligne ")
            thumbnail_url = baseUrl + document.selectFirst("meta[property=og:image]")?.attr("content")
            status = SManga.UNKNOWN
        }

        // The second <select> in #navigation is the chapter dropdown (descending order).
        val chapterList = document.select("#navigation select").getOrNull(1)
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

        return SMangaUpdate(updatedManga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}/1").asJsoup()
        return document.select("#navigation select").getOrNull(2)
            ?.select("option")
            ?.mapIndexed { index, option ->
                Page(index, url = option.attr("abs:value"))
            }
            .orEmpty()
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url).asJsoup().selectFirst("#image img")?.attr("abs:src").orEmpty()

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
