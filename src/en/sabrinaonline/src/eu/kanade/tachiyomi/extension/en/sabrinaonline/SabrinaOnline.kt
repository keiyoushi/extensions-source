package eu.kanade.tachiyomi.extension.en.sabrinaonline

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
abstract class SabrinaOnline : KeiSource() {

    override val supportsLatest = false

    fun manga(): SManga = SManga.create().apply {
        title = "Sabrina Online"
        thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
        artist = "Eric W. Schwartz"
        author = "Eric W. Schwartz"
        status = SManga.UNKNOWN
        setUrlWithoutDomain("$baseUrl/archive.html")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(manga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga(), chapters)

        val document = client.get("$baseUrl/archive.html").asJsoup()
        val chapterList = mutableListOf<SChapter>()

        // turn cells into chapters, with the section name if present
        fun trToChapters(tr: Element, sections: List<String?>): List<SChapter> {
            val chapters = mutableListOf<SChapter>()

            tr.select("td").forEachIndexed { index, td ->
                td.select("a").forEach { a ->
                    val chapter = a.text()
                    if (chapter.isEmpty()) return@forEach

                    val hasYear = sections.getOrNull(index)?.let { it.isNotEmpty() && it.first().isDigit() } ?: false
                    chapters.add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(a.absUrl("href"))
                            name = if (hasYear) "${sections[index]} $chapter" else chapter
                        },
                    )
                }
            }

            return chapters
        }

        document.select("center table tr").chunked(2).forEach { pair ->
            if (pair.size < 2) {
                chapterList.addAll(trToChapters(pair[0], listOf()))
            } else {
                val sections = pair[0].select("td").map { it.text().trim() }
                // use the section names if there are any in the first row
                if (sections.isNotEmpty()) {
                    chapterList.addAll(trToChapters(pair[1], sections))
                } else {
                    chapterList.addAll(trToChapters(pair[1], listOf()))
                    chapterList.addAll(trToChapters(pair[0], listOf()))
                }
            }
        }

        return SMangaUpdate(manga(), chapterList.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val pages = document.select("center img").filter {
            it.hasAttr("src") && (
                it.attr("src").contains("strips/") ||
                    it.attr("src").contains("pages/")
                )
        }

        return pages.mapIndexed { index, img ->
            // use full image instead of preview if available
            if (img.parent()?.tagName() == "a") {
                val parent = img.parent()!!
                val href = parent.absUrl("href").ifEmpty { parent.attr("href") }
                Page(index, imageUrl = href)
            } else {
                val src = img.absUrl("src").ifEmpty { img.attr("src") }
                Page(index, imageUrl = src)
            }
        }
    }
}
