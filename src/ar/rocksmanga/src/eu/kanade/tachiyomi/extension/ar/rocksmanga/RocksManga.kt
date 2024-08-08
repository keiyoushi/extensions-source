package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RocksManga : Madara(
    "Rocks Manga",
    "https://rocksmanga.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
) {

    override fun popularMangaSelector() = "div.page-content-listing > .manga"
    override val popularMangaUrlSelector = "div.manga-poster a"

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            title = element.selectFirst(popularMangaUrlSelector)!!.attr("title")
        }
    }

    override fun searchMangaSelector() = "#manga-search-results .manga-item"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            selectFirst("a.cover")!!.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.attr("title")
            }
            selectFirst("img")?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override val mangaDetailsSelectorTitle = ".manga-title"
    override val mangaDetailsSelectorAuthor = "div.meta span:contains(المؤلف:) + span a"
    override val mangaDetailsSelectorArtist = "div.meta span:contains(الرسام:) + span a"
    override val mangaDetailsSelectorStatus = ".status"
    override val mangaDetailsSelectorDescription = "div.description"
    override val mangaDetailsSelectorThumbnail = ".manga-poster img"
    override val mangaDetailsSelectorGenre = "div.meta span:contains(التصنيف:) + span a"
    override val altNameSelector = "div.alternative"
    override fun chapterListSelector() = ".chapters-list li.chapter-item"
    override fun chapterDateSelector() = ".chapter-release-date"
    override val pageListParseSelector = ".chapter-reading-page img"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
    override val fetchGenres = false
    override val filterNonMangaItems = false

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        chapter.name = element.selectFirst(".num")!!.text()
        return chapter
    }
}
